package org.example.kinetiqfun

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import android.graphics.BitmapFactory

class GameFourActivity : BasePoseActivity() {

    private var nameP1 = "Player 1"
    private var nameP2 = "Player 2"

    private var currentRound = 1
    private var p1Score = 0
    private var p2Score = 0
    private val totalRounds = 4

    private lateinit var txtRound: TextView
    private lateinit var txtP1Score: TextView
    private lateinit var txtP2Score: TextView
    private lateinit var imgTargetPose: ImageView
    private lateinit var txtTimer: TextView
    private lateinit var polaroidContainer: View
    private lateinit var imgSnapshotP1: ImageView
    private lateinit var imgSnapshotP2: ImageView
    private lateinit var txtP1Sim: TextView
    private lateinit var txtP2Sim: TextView
    private lateinit var txtRoundWinner: TextView
    private var captureSfxId: Int = 0

    private val poseImages = listOf(
        R.drawable.posebuaya,
        R.drawable.posedino,
        R.drawable.posekucing,
        R.drawable.poserocket
    )
    
    // Map of drawable ID to its extracted feature array
    private val targetFeaturesMap = mutableMapOf<Int, FloatArray>()
    
    // Shuffled round order
    private var roundOrder = listOf<Int>()
    
    // Latest poses detected from camera
    private var latestP1Pose: Pose? = null
    private var latestP2Pose: Pose? = null
    
    private var currentTargetDrawableId = 0
    private var isGameStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inflate our custom overlay and add it to the root
        val overlayView = LayoutInflater.from(this).inflate(R.layout.layout_game_three_overlay, binding.root as ViewGroup, false)
        (binding.root as ViewGroup).addView(overlayView)

        txtRound = overlayView.findViewById(R.id.txtRound)
        txtP1Score = overlayView.findViewById(R.id.txtP1Score)
        txtP2Score = overlayView.findViewById(R.id.txtP2Score)
        imgTargetPose = overlayView.findViewById(R.id.imgTargetPose)
        txtTimer = overlayView.findViewById(R.id.txtTimer)
        polaroidContainer = overlayView.findViewById(R.id.polaroidContainer)
        imgSnapshotP1 = overlayView.findViewById(R.id.imgSnapshotP1)
        imgSnapshotP2 = overlayView.findViewById(R.id.imgSnapshotP2)
        txtP1Sim = overlayView.findViewById(R.id.txtP1Sim)
        txtP2Sim = overlayView.findViewById(R.id.txtP2Sim)
        txtRoundWinner = overlayView.findViewById(R.id.txtRoundWinner)

        // For now, no background music or a specific one. Let's use upbeat.
        (application as KinetiqFunApp).changeMusic(R.raw.tiru_gaya)
        
        // Tell OverlayView to draw Tiru Gaya mode (which can just be standard skeletons)
        binding.overlayView.setGameMode(OverlayView.GameMode.TIRU_GAYA)
        
        captureSfxId = soundPool.load(this, R.raw.capture, 1)

        // Pre-process all target poses
        loadTargetPoses()
    }

    private fun loadTargetPoses() {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        val detector = PoseDetection.getClient(options)
        
        var loadedCount = 0
        
        for (imgResId in poseImages) {
            val bitmap = BitmapFactory.decodeResource(resources, imgResId)
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { pose ->
                    val features = PoseEvaluator.extractFeatures(pose)
                    if (features != null) {
                        targetFeaturesMap[imgResId] = features
                    } else {
                        // Fallback dummy features if ML Kit fails on cartoon
                        targetFeaturesMap[imgResId] = FloatArray(8) { 0f }
                    }
                }
                .addOnFailureListener {
                    targetFeaturesMap[imgResId] = FloatArray(8) { 0f }
                }
                .addOnCompleteListener {
                    loadedCount++
                    if (loadedCount == poseImages.size) {
                        startGame()
                    }
                }
        }
    }

    private fun startGame() {
        roundOrder = targetFeaturesMap.keys.shuffled()
        currentRound = 1
        p1Score = 0
        p2Score = 0
        updateScoreBoard()
        startRound()
    }

    private fun updateScoreBoard() {
        // Create stars based on score
        val p1Stars = "⭐".repeat(p1Score)
        val p2Stars = "⭐".repeat(p2Score)
        
        txtP1Score.text = "P1 SKOR: $p1Stars"
        txtP2Score.text = "$p2Stars :SKOR P2"
    }

    private fun startRound() {
        if (currentRound <= totalRounds) {
            currentTargetDrawableId = roundOrder[currentRound - 1]
        } else {
            // Sudden death
            currentTargetDrawableId = roundOrder.random()
        }
        
        updateScoreBoard()
        imgTargetPose.setImageResource(currentTargetDrawableId)
        imgTargetPose.visibility = View.VISIBLE
        polaroidContainer.visibility = View.GONE
        
        isGameStarted = true
        txtTimer.visibility = View.VISIBLE
        
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                animateCountdownText(txtTimer, (millisUntilFinished / 1000 + 1).toString())
            }
            override fun onFinish() {
                txtTimer.visibility = View.GONE
                captureAndEvaluate()
            }
        }.start()
    }

    private fun captureAndEvaluate() {
        isGameStarted = false
        
        // Play capture sfx
        if (captureSfxId != 0) {
            soundPool.play(captureSfxId, 1f, 1f, 0, 0, 1f)
        }
        
        // Flash effect
        val flashView = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
            alpha = 1f
            elevation = 100f
        }
        (binding.root as ViewGroup).addView(flashView)
        flashView.animate().alpha(0f).setDuration(400).withEndAction {
            (binding.root as ViewGroup).removeView(flashView)
        }.start()
        
        // 1. Take Snapshot from camera ONLY (no skeleton)
        val fullBitmap = binding.previewView.bitmap
        
        if (fullBitmap != null) {
            val width = fullBitmap.width
            val height = fullBitmap.height
            val midX = width / 2
            
            // Crop for P1 (Left) and P2 (Right)
            val bmpP1 = Bitmap.createBitmap(fullBitmap, 0, 0, midX, height)
            val bmpP2 = Bitmap.createBitmap(fullBitmap, midX, 0, width - midX, height)
            
            imgSnapshotP1.setImageBitmap(bmpP1)
            imgSnapshotP2.setImageBitmap(bmpP2)
        }
        
        // 2. Evaluate
        val targetFeatures = targetFeaturesMap[currentTargetDrawableId] ?: FloatArray(8) { 0f }
        
        val p1Sim = if (latestP1Pose != null) {
            val p1Features = PoseEvaluator.extractFeatures(latestP1Pose!!)
            if (p1Features != null) PoseEvaluator.compareFeatures(targetFeatures, p1Features) else 0f
        } else 0f
        
        val p2Sim = if (latestP2Pose != null) {
            val p2Features = PoseEvaluator.extractFeatures(latestP2Pose!!)
            if (p2Features != null) PoseEvaluator.compareFeatures(targetFeatures, p2Features) else 0f
        } else 0f
        
        txtP1Sim.text = "$nameP1: ${p1Sim.toInt()}%"
        txtP2Sim.text = "$nameP2: ${p2Sim.toInt()}%"
        
        if (p1Sim > p2Sim) {
            p1Score++
            txtRoundWinner.text = "P1 LEBIH MANTAP!"
            txtRoundWinner.setTextColor(Color.parseColor("#FFDD00"))
        } else if (p2Sim > p1Sim) {
            p2Score++
            txtRoundWinner.text = "P2 LEBIH MANTAP!"
            txtRoundWinner.setTextColor(Color.parseColor("#FFDD00"))
        } else {
            txtRoundWinner.text = "GAYA SEIMBANG!"
            txtRoundWinner.setTextColor(Color.parseColor("#FFFFFF"))
        }
        
        updateScoreBoard()
        polaroidContainer.visibility = View.VISIBLE
        
        // 3. Wait 5 seconds, then next round
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (currentRound < totalRounds) {
                    currentRound++
                    startRound()
                } else if (currentRound == totalRounds && p1Score == p2Score) {
                    currentRound++
                    startRound()
                } else {
                    showFinalWinner()
                }
            }
        }.start()
    }

    private fun showFinalWinner() {
        polaroidContainer.visibility = View.GONE
        imgTargetPose.visibility = View.GONE
        
        val winner = if (p1Score > p2Score) nameP1 else nameP2
        
        runOnUiThread {
            binding.gameOverLayout.visibility = View.VISIBLE
            binding.winnerText.visibility = View.GONE // using OverlayView's winner text
            binding.overlayView.setWinner(winner)
            
            if (soundIdVictory != 0) soundPool.play(soundIdVictory, 1f, 1f, 1, 0, 1f)
            

        }
    }

    override fun onPoseDetected(playerId: Int, pose: Pose, pXOffset: Float, imgWidth: Int, imgHeight: Int) {
        if (playerId == 1) latestP1Pose = pose
        if (playerId == 2) latestP2Pose = pose
    }
}
