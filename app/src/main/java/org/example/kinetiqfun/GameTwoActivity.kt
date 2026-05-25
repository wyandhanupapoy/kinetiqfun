package org.example.kinetiqfun

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class GameTwoActivity : BasePoseActivity() {

    private var nameP1 = "Player 1"
    private var nameP2 = "Player 2"
    private var isGameStarted = false
    private var winner: String? = null
    
    private var p1Progress = 0f
    private var p2Progress = 0f
    
    // Hip Tracking
    private var lastP1HipX = 0f
    private var lastP2HipX = 0f
    private var p1Direction = 0 // 1 for right, -1 for left
    private var p2Direction = 0

    // Waiting logic
    private var isWaitingForPlayers = false
    private var lastP1DetectTime = 0L
    private var lastP2DetectTime = 0L
    
    // Stability sensor
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isDeviceStable = false
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var stableCount = 0

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val delta = Math.abs(x - lastAccelX) + Math.abs(y - lastAccelY) + Math.abs(z - lastAccelZ)
            
            // Check if device is laying flat (less strict, allow up to 8.5)
            val isNotLayingFlat = Math.abs(z) < 8.5f
            
            if (delta < 1.0f && isNotLayingFlat) {
                stableCount++
                if (stableCount > 10) isDeviceStable = true
            } else {
                stableCount = 0
                isDeviceStable = false
            }
            
            lastAccelX = x
            lastAccelY = y
            lastAccelZ = z
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isGameStarted = false // Wait for tutorial
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        (application as KinetiqFunApp).changeMusic(R.raw.kicau_mania_background_music)
        
        val videoPath = "android.resource://$packageName/${R.raw.tutorial_geol_kicau_mania}"
        binding.tutorialVideoView.setVideoURI(Uri.parse(videoPath))
        binding.tutorialVideoView.setZOrderMediaOverlay(true)
        binding.tutorialVideoContainer.visibility = View.VISIBLE
        binding.tutorialVideoView.start()
        
        var tutorialTimer: CountDownTimer? = null
        
        binding.tutorialVideoView.setOnPreparedListener { mp ->
            val durationMs = mp.duration.toLong()
            tutorialTimer = object : CountDownTimer(durationMs, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    if (isDestroyed || isFinishing) {
                        cancel()
                        return
                    }
                    val secondsLeft = millisUntilFinished / 1000
                    binding.tutorialCountdownText.text = String.format("%02d", secondsLeft)
                }
                override fun onFinish() {
                    if (isDestroyed || isFinishing) return
                    binding.tutorialCountdownText.text = "00"
                }
            }.start()
        }
        
        binding.tutorialVideoView.setOnCompletionListener {
            tutorialTimer?.cancel()
            binding.tutorialVideoContainer.visibility = View.GONE
            startWaitingForPlayers()
        }
        
        binding.tutorialVideoView.setOnErrorListener { _, _, _ ->
            tutorialTimer?.cancel()
            binding.tutorialVideoContainer.visibility = View.GONE
            startWaitingForPlayers()
            true
        }
        
        binding.overlayView.currentGameMode = OverlayView.GameMode.BALAP_GEOL
        binding.overlayView.updateBalapGeolState(0f, 0f, nameP1, nameP2, null)
        
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun startWaitingForPlayers() {
        isWaitingForPlayers = true
        binding.waitingLayout.visibility = View.VISIBLE
        
        val handler = Handler(Looper.getMainLooper())
        val checkRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) return
                if (!isWaitingForPlayers) return
                
                val now = System.currentTimeMillis()
                val p1Detected = (now - lastP1DetectTime) < 1500
                val p2Detected = (now - lastP2DetectTime) < 1500
                
                if (p1Detected && p2Detected && isDeviceStable) {
                    isWaitingForPlayers = false
                    binding.waitingLayout.visibility = View.GONE
                    startCountdown()
                } else {
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(checkRunnable)
    }

    private fun startCountdown() {
        binding.countdownText.visibility = View.VISIBLE
        var toneGen: ToneGenerator? = null
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        object : CountDownTimer(4000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000)
                if (seconds > 0) {
                    animateCountdownText(binding.countdownText, seconds.toString())
                    try {
                        toneGen?.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                    } catch (e: Exception) {}
                } else {
                    animateCountdownText(binding.countdownText, "GO!")
                    try {
                        toneGen?.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 300)
                    } catch (e: Exception) {}
                }
            }
            
            override fun onFinish() {
                if (isDestroyed || isFinishing) return
                binding.countdownText.visibility = View.GONE
                isGameStarted = true
                toneGen?.release()
            }
        }.start()
    }

    override fun onPoseDetected(playerId: Int, pose: Pose, pXOffset: Float, imgWidth: Int, imgHeight: Int) {
        // Update detection time early so WAITING doesn't get stuck on poor hip likelihood
        if (playerId == 1) lastP1DetectTime = System.currentTimeMillis()
        if (playerId == 2) lastP2DetectTime = System.currentTimeMillis()

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        if (leftHip == null || rightHip == null || leftHip.inFrameLikelihood < 0.5f || rightHip.inFrameLikelihood < 0.5f) return

        val hipX = (leftHip.position.x + rightHip.position.x) / 2f
        var isGeol = false
        
        // Use the playerId passed from BasePoseActivity
        val isPlayer1 = (playerId == 1)

        if (!isGameStarted || winner != null) return

        if (isPlayer1) {
            if (lastP1HipX != 0f) {
                val delta = hipX - lastP1HipX
                // Threshold goyangan agar harus selebar mungkin (30f)
                if (Math.abs(delta) > 30f) { 
                    val newDir = if (delta > 0) 1 else -1
                    if (p1Direction != 0 && p1Direction != newDir) {
                        isGeol = true
                    }
                    p1Direction = newDir
                }
            }
            lastP1HipX = hipX
            
            if (isGeol) {
                p1Progress = (p1Progress + 0.015f).coerceAtMost(1f) // Dorongan keatas sedikit demi sedikit
                if (soundIdAction != 0) soundPool.play(soundIdAction, 1f, 1f, 0, 0, 1.0f + (p1Progress * 0.5f))
                if (p1Progress >= 1f && winner == null) {
                    winner = nameP1
                    showWinner()
                }
            }
        } else {
            if (lastP2HipX != 0f) {
                val delta = hipX - lastP2HipX
                if (Math.abs(delta) > 30f) { 
                    val newDir = if (delta > 0) 1 else -1
                    if (p2Direction != 0 && p2Direction != newDir) {
                        isGeol = true
                    }
                    p2Direction = newDir
                }
            }
            lastP2HipX = hipX
            
            if (isGeol) {
                p2Progress = (p2Progress + 0.015f).coerceAtMost(1f)
                if (p2Progress >= 1f && winner == null) {
                    winner = nameP2
                    showWinner()
                }
            }
        }

        runOnUiThread {
            binding.overlayView.updateBalapGeolState(p1Progress, p2Progress, nameP1, nameP2, winner)
        }
    }
    
    private fun showWinner() {
        runOnUiThread {
            binding.gameOverLayout.visibility = View.VISIBLE
            binding.winnerText.visibility = View.VISIBLE
            
            winner?.let {
                binding.winnerText.text = getString(R.string.winner_text_format, it)
                binding.overlayView.setWinner(it)
            }
            if (soundIdVictory != 0) soundPool.play(soundIdVictory, 1f, 1f, 1, 0, 1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        (application as KinetiqFunApp).changeMusic(0)
    }
}