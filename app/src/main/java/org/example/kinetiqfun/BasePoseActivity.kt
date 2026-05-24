package org.example.kinetiqfun

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        
        // Load sounds from res/raw safely to avoid build errors if they don't exist yet
        val actionId = resources.getIdentifier("action", "raw", packageName)
        if (actionId != 0) soundIdAction = soundPool.load(this, actionId, 1)

        val victoryId = resources.getIdentifier("victory", "raw", packageName)
        if (victoryId != 0) soundIdVictory = soundPool.load(this, victoryId, 1)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newFixedThreadPool(2)

        binding.cameraSwitchButton.setOnClickListener {
            isFrontCamera = !isFrontCamera
            startCamera()
        }
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
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processMultiplayer(imageProxy)
                    }
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
        val bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        val width = rotatedBitmap.width
        val height = rotatedBitmap.height
        val halfWidth = width / 2

        // Potongan Kiri Sensor
        val leftBitmap = Bitmap.createBitmap(rotatedBitmap, 0, 0, halfWidth, height)
        val leftImage = InputImage.fromBitmap(leftBitmap, 0)

        // Potongan Kanan Sensor
        val rightBitmap = Bitmap.createBitmap(rotatedBitmap, halfWidth, 0, halfWidth, height)
        val rightImage = InputImage.fromBitmap(rightBitmap, 0)

        // Mapping agar Player 1 selalu di KIRI LAYAR dan Player 2 di KANAN LAYAR
        // Kamera Depan (Mirrored): Sensor Kanan -> Layar Kiri (P1), Sensor Kiri -> Layar Kanan (P2)
        val p1Input = if (isFrontCamera) rightImage else leftImage
        val p1Offset = if (isFrontCamera) halfWidth.toFloat() else 0f
        
        val p2Input = if (isFrontCamera) leftImage else rightImage
        val p2Offset = if (isFrontCamera) 0f else halfWidth.toFloat()

        val task1 = poseDetector1.process(p1Input)
            .addOnSuccessListener { pose ->
                if (pose.allPoseLandmarks.size > 15) {
                    binding.overlayView.setResults(1, pose, width, height, isFrontCamera, p1Offset)
                    onPoseDetected(1, pose, p1Offset, width, height)
                } else {
                    binding.overlayView.setResults(1, null, width, height, isFrontCamera, p1Offset)
                }
            }
            .addOnFailureListener {
                binding.overlayView.setResults(1, null, width, height, isFrontCamera, p1Offset)
            }

        val task2 = poseDetector2.process(p2Input)
            .addOnSuccessListener { pose ->
                if (pose.allPoseLandmarks.size > 15) {
                    binding.overlayView.setResults(2, pose, width, height, isFrontCamera, p2Offset)
                    onPoseDetected(2, pose, p2Offset, width, height)
                } else {
                    binding.overlayView.setResults(2, null, width, height, isFrontCamera, p2Offset)
                }
            }
            .addOnFailureListener {
                binding.overlayView.setResults(2, null, width, height, isFrontCamera, p2Offset)
            }

        Tasks.whenAllComplete(task1, task2).addOnCompleteListener {
            imageProxy.close()
            leftBitmap.recycle()
            rightBitmap.recycle()
            rotatedBitmap.recycle()
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
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
