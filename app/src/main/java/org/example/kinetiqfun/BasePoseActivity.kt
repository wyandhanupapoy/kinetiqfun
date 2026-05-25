package org.example.kinetiqfun

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.app.AlertDialog
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.tasks.Tasks
import androidx.camera.mlkit.vision.MlKitAnalyzer
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.example.kinetiqfun.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

abstract class BasePoseActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    protected var isFrontCamera = true
    
    private val poseDetector1 = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )
    private val poseDetector2 = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val segmenter1 = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
    )
    private val segmenter2 = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
    )

    protected lateinit var soundPool: SoundPool
    protected var soundIdAction: Int = 0
    protected var soundIdVictory: Int = 0

    fun playVictorySound() {
        if (soundIdVictory != 0) {
            soundPool.play(soundIdVictory, 1f, 1f, 1, 0, 1f)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        
        // Load sounds from res/raw safely to avoid build errors if they don't exist yet
        // val actionId = resources.getIdentifier("box_crack", "raw", packageName)
        // if (actionId != 0) soundIdAction = soundPool.load(this, actionId, 1)

        val victoryId = resources.getIdentifier("win_sfx", "raw", packageName)
        if (victoryId != 0) soundIdVictory = soundPool.load(this, victoryId, 1)
        else {
            val oldVictoryId = resources.getIdentifier("victory", "raw", packageName)
            if (oldVictoryId != 0) soundIdVictory = soundPool.load(this, oldVictoryId, 1)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newFixedThreadPool(2)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val dialog = android.app.Dialog(this@BasePoseActivity)
                dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                
                val rootLayout = android.widget.LinearLayout(this@BasePoseActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#E6000000")) // Semi-transparent black
                    setPadding(64, 64, 64, 64)
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val typeface = androidx.core.content.res.ResourcesCompat.getFont(this@BasePoseActivity, R.font.game_font)

                val titleText = android.widget.TextView(this@BasePoseActivity).apply {
                    text = getString(R.string.quit_game)
                    setTextColor(Color.parseColor("#FF4444"))
                    textSize = 48f
                    setTypeface(typeface)
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 0, 0, 32)
                }
                
                val msgText = android.widget.TextView(this@BasePoseActivity).apply {
                    text = getString(R.string.quit_confirm)
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    setTypeface(typeface)
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 0, 0, 48)
                }

                val buttonsLayout = android.widget.LinearLayout(this@BasePoseActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }

                val btnNo = android.widget.Button(this@BasePoseActivity).apply {
                    text = getString(R.string.no)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.DKGRAY)
                    textSize = 24f
                    setTypeface(typeface)
                    setPadding(32, 16, 32, 16)
                    setOnClickListener {
                        SoundManager.playClick()
                        dialog.dismiss()
                    }
                }

                val btnYes = android.widget.Button(this@BasePoseActivity).apply {
                    text = getString(R.string.yes)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#FF4444"))
                    textSize = 24f
                    setTypeface(typeface)
                    setPadding(32, 16, 32, 16)
                    val params = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(32, 0, 0, 0) }
                    layoutParams = params
                    setOnClickListener {
                        SoundManager.playClick()
                        dialog.dismiss()
                        (application as KinetiqFunApp).changeMusic(R.raw.background_music)
                        finish()
                    }
                }

                buttonsLayout.addView(btnNo)
                buttonsLayout.addView(btnYes)

                rootLayout.addView(titleText)
                rootLayout.addView(msgText)
                rootLayout.addView(buttonsLayout)

                dialog.setContentView(rootLayout)
                dialog.window?.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                dialog.show()
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val aspectRatio = AspectRatio.RATIO_16_9
            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            // Optimized Analyzer using manual ImageAnalysis but with direct InputImage.fromMediaImage
            // to avoid extra Bitmap allocations while maintaining control over the flow.
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                processMultiplayer(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("KinetiQ", "Binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processMultiplayer(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val rotation = imageProxy.imageInfo.rotationDegrees
        
        // Use InputImage.fromMediaImage directly - NO BITMAP CONVERSION HERE
        val fullInputImage = InputImage.fromMediaImage(mediaImage, rotation)
        
        val width = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val height = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        
        // Current logic splits screen in two for 2 players.
        // To avoid Bitmaps, we pass the full image to both detectors.
        // We will filter landmarks in OverlayView based on their X coordinate.

        var p1Pose: Pose? = null
        var p1Mask: SegmentationMask? = null
        var p2Pose: Pose? = null
        var p2Mask: SegmentationMask? = null

        val task1Pose = poseDetector1.process(fullInputImage).addOnSuccessListener { p1Pose = it }
        val task1Mask = segmenter1.process(fullInputImage).addOnSuccessListener { p1Mask = it }
        val task2Pose = poseDetector2.process(fullInputImage).addOnSuccessListener { p2Pose = it }
        val task2Mask = segmenter2.process(fullInputImage).addOnSuccessListener { p2Mask = it }

        Tasks.whenAllComplete(task1Pose, task1Mask, task2Pose, task2Mask).addOnCompleteListener {
            if (p1Pose != null) {
                // We pass 0f as offset because we are processing full image now
                binding.overlayView.setResults(1, p1Pose, p1Mask, width, height, isFrontCamera, 0f)
                onPoseDetected(1, p1Pose!!, 0f, width, height)
            }
            if (p2Pose != null) {
                binding.overlayView.setResults(2, p2Pose, p2Mask, width, height, isFrontCamera, 0f)
                onPoseDetected(2, p2Pose!!, 0f, width, height)
            }
            
            imageProxy.close()
        }
    }

    open fun onPoseDetected(playerId: Int, pose: Pose, pXOffset: Float, imgWidth: Int, imgHeight: Int) {}

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector1.close()
        poseDetector2.close()
        segmenter1.close()
        segmenter2.close()
        soundPool.release()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    protected fun animateCountdownText(textView: android.widget.TextView, text: String) {
        textView.text = text
        textView.scaleX = 0.5f
        textView.scaleY = 0.5f
        textView.alpha = 0f
        textView.visibility = android.view.View.VISIBLE
        textView.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .alpha(1f)
            .setDuration(400)
            .withEndAction {
                textView.animate().scaleX(1f).scaleY(1f).setDuration(400).start()
            }
            .start()
    }
}
