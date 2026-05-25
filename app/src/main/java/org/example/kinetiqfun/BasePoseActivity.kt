package org.example.kinetiqfun

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
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
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
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
        
        // Load hit/action sound effect
        val actionId = resources.getIdentifier("box_crack", "raw", packageName)
        if (actionId != 0) soundIdAction = soundPool.load(this, actionId, 1)

        val victoryId = resources.getIdentifier("win_sfx", "raw", packageName)
        if (victoryId != 0) soundIdVictory = soundPool.load(this, victoryId, 1)
        else {
            val oldVictoryId = resources.getIdentifier("victory", "raw", packageName)
            if (oldVictoryId != 0) soundIdVictory = soundPool.load(this, oldVictoryId, 1)
        }
        
        binding.btnPlayAgain.setOnClickListener {
            SoundManager.playClick()
            recreate()
        }
        
        binding.btnBackMenu.setOnClickListener {
            SoundManager.playClick()
            (application as KinetiqFunApp).changeMusic(0) // Default BGM
            finish()
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

    /**
     * Splits the camera frame into left/right halves and runs a separate pose detector
     * on each half. This is necessary because ML Kit's single-person pose detector
     * only detects ONE person per image — running two detectors on the same full image
     * would return the same pose for both players.
     */
    @OptIn(ExperimentalGetImage::class)
    private fun processMultiplayer(imageProxy: ImageProxy) {
        val rotation = imageProxy.imageInfo.rotationDegrees

        // toBitmap() is stable in CameraX 1.4.x — returns the raw sensor image as Bitmap
        val rawBitmap: Bitmap
        try {
            rawBitmap = imageProxy.toBitmap()
        } catch (e: Exception) {
            imageProxy.close()
            return
        }

        // Apply rotation so the bitmap matches what the user sees
        val rotatedBitmap = if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                if (it !== rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        val fullWidth = rotatedBitmap.width
        val fullHeight = rotatedBitmap.height
        val halfWidth = fullWidth / 2

        // Split into left and right halves
        val leftHalf = Bitmap.createBitmap(rotatedBitmap, 0, 0, halfWidth, fullHeight)
        val rightHalf = Bitmap.createBitmap(rotatedBitmap, halfWidth, 0, fullWidth - halfWidth, fullHeight)

        // For front camera the image is mirrored:
        //   Screen-left (P1) = raw image RIGHT half
        //   Screen-right (P2) = raw image LEFT half
        // For back camera it's direct:
        //   Screen-left (P1) = raw image LEFT half
        //   Screen-right (P2) = raw image RIGHT half
        val p1Half = if (isFrontCamera) rightHalf else leftHalf
        val p2Half = if (isFrontCamera) leftHalf else rightHalf
        val p1Offset = if (isFrontCamera) halfWidth.toFloat() else 0f
        val p2Offset = if (isFrontCamera) 0f else halfWidth.toFloat()

        val p1Input = com.google.mlkit.vision.common.InputImage.fromBitmap(p1Half, 0)
        val p2Input = com.google.mlkit.vision.common.InputImage.fromBitmap(p2Half, 0)

        var p1Pose: Pose? = null
        var p2Pose: Pose? = null

        val task1 = poseDetector1.process(p1Input).addOnSuccessListener { p1Pose = it }
        val task2 = poseDetector2.process(p2Input).addOnSuccessListener { p2Pose = it }

        Tasks.whenAllComplete(task1, task2).addOnCompleteListener {
            if (p1Pose != null && PoseEvaluator.isLikelyHuman(p1Pose!!)) {
                binding.overlayView.setResults(1, p1Pose, null, fullWidth, fullHeight, isFrontCamera, p1Offset)
                onPoseDetected(1, p1Pose!!, p1Offset, fullWidth, fullHeight)
            } else {
                binding.overlayView.setResults(1, null, null, fullWidth, fullHeight, isFrontCamera, 0f)
            }

            if (p2Pose != null && PoseEvaluator.isLikelyHuman(p2Pose!!)) {
                binding.overlayView.setResults(2, p2Pose, null, fullWidth, fullHeight, isFrontCamera, p2Offset)
                onPoseDetected(2, p2Pose!!, p2Offset, fullWidth, fullHeight)
            } else {
                binding.overlayView.setResults(2, null, null, fullWidth, fullHeight, isFrontCamera, 0f)
            }

            // Recycle all intermediate bitmaps
            leftHalf.recycle()
            rightHalf.recycle()
            rotatedBitmap.recycle()

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
        soundPool.release()
        try {
            binding.overlayView.recycleBitmaps()
        } catch (e: Exception) {}
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
