package org.example.kinetiqfun

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.CountDownTimer
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.max
import org.example.kinetiqfun.TooltipHelper
import org.example.kinetiqfun.TooltipManager

class KesatriaPCDActivity : BasePoseActivity() {

    private val rocks = mutableListOf<OverlayView.Rock>()
    private var nameP1 = "Player 1"
    private var nameP2 = "Player 2"
    private var isInitialized = false
    private var winner: String? = null
    private var isGameStarted = false

    private val lastHandY = mutableMapOf<Int, Float>()
    
    // Race Mode State
    private val totalRocksGoal = 5
    private var p1DestroyedCount = 0
    private var p2DestroyedCount = 0
    private var nextRockId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isGameStarted = false // Wait for tutorial
        
        val videoPath = "android.resource://$packageName/${R.raw.tutorial_kesatriapcd}"
        binding.tutorialVideoView.setVideoURI(Uri.parse(videoPath))
        // Gunakan setZOrderMediaOverlay agar video di atas kamera tapi di bawah teks
        binding.tutorialVideoView.setZOrderMediaOverlay(true)
        binding.tutorialVideoContainer.visibility = View.VISIBLE
        binding.tutorialVideoView.start()
        
        var tutorialTimer: android.os.CountDownTimer? = null
        
        binding.tutorialVideoView.setOnPreparedListener { mp ->
            val durationMs = mp.duration.toLong()
            tutorialTimer = object : android.os.CountDownTimer(durationMs, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = millisUntilFinished / 1000
                    binding.tutorialCountdownText.text = String.format("%02d", secondsLeft)
                }
                override fun onFinish() {
                    binding.tutorialCountdownText.text = "00"
                }
            }.start()
        }
        
        binding.tutorialVideoView.setOnCompletionListener {
            tutorialTimer?.cancel()
            binding.tutorialVideoContainer.visibility = View.GONE
            startCountdown()
        }
        
        binding.tutorialVideoView.setOnErrorListener { _, _, _ ->
            tutorialTimer?.cancel()
            binding.tutorialVideoContainer.visibility = View.GONE
            startCountdown()
            true // Handled, prevent crash
        }
        
        // Show tooltips for first-time players
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (!isInitialized) {
                    initRocks(binding.overlayView.width, binding.overlayView.height)
                }
                showInitialTooltips()
            }
        })
    }

    private fun startCountdown() {
        binding.countdownText.visibility = View.VISIBLE
        var toneGen: ToneGenerator? = null
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                binding.countdownText.text = seconds.toString()
                try {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                } catch (e: Exception) {}
            }

            override fun onFinish() {
                try {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
                } catch (e: Exception) {}
                binding.countdownText.visibility = View.INVISIBLE
                isGameStarted = true
                
                // Release on a background thread to prevent blocking the UI thread (lag spike)
                Thread {
                    try {
                        Thread.sleep(1000)
                        toneGen?.release()
                    } catch (e: Exception) {}
                }.start()
            }
        }.start()
    }

    private fun initRocks(width: Int, height: Int) {
        rocks.clear()
        
        // Spawn exactly 5 rocks per player
        for (i in 0 until 5) {
            spawnRockForPlayer(1, width, height, i)
            spawnRockForPlayer(2, width, height, i)
        }
        isInitialized = true
        
        // Push state immediately so rocks are pre-drawn during the countdown/tutorial
        runOnUiThread {
            binding.overlayView.updateKesatriaState(rocks, p1DestroyedCount, p2DestroyedCount, nameP1, nameP2, winner)
        }
    }

    private fun spawnRockForPlayer(playerId: Int, screenWidth: Int, screenHeight: Int, index: Int) {
        val rockWidth = screenWidth / 14f // Made rocks smaller
        val rockHeight = rockWidth * 1.1f
        
        // Player 1 area is [0, screenWidth/2], Player 2 area is [screenWidth/2, screenWidth]
        val areaWidth = screenWidth / 2f
        val startX = if (playerId == 1) 0f else areaWidth
        
        // Exact slots: index 0,1,2 on bottom layer. index 3,4 on middle layer.
        val layer = if (index < 3) 0 else 1
        val slotMultipliers = listOf(0.2f, 0.5f, 0.8f, 0.35f, 0.65f)
        val slotMultiplier = slotMultipliers[index % slotMultipliers.size]
        
        var x = startX + (areaWidth * slotMultiplier) - (rockWidth / 2f)
        
        // Keep within bounds
        val minX = if (playerId == 1) 0f else screenWidth / 2f
        val maxX = if (playerId == 1) screenWidth / 2f - rockWidth else screenWidth - rockWidth
        x = maxOf(minX, minOf(x, maxX))
        
        // Static positioning from the very bottom
        val baseY = screenHeight.toFloat()
        val startY = baseY - (rockHeight * (layer + 1))
        
        val newRock = OverlayView.Rock(
            id = nextRockId++, 
            rect = RectF(x, startY, x + rockWidth, startY + rockHeight),
            velocityY = 0f
        )
        rocks.add(newRock)
    }

    override fun onPoseDetected(playerId: Int, pose: Pose, pXOffset: Float, imgWidth: Int, imgHeight: Int) {
        if (!isGameStarted || winner != null) return
        
        val overlay = binding.overlayView
        // Only initialize when view has realistic dimensions
        if (overlay.width < 200 || overlay.height < 200) return

        if (!isInitialized) {
            initRocks(overlay.width, overlay.height)
        }

        val scale = max(overlay.width.toFloat() / imgWidth, overlay.height.toFloat() / imgHeight)
        val canvasOffsetX = (overlay.width - imgWidth * scale) / 2f
        val canvasOffsetY = (overlay.height - imgHeight * scale) / 2f

        fun getScreenX(x: Float): Float {
            var sx = (x + pXOffset) * scale + canvasOffsetX
            if (isFrontCamera) sx = overlay.width - sx
            return sx
        }
        fun getScreenY(y: Float): Float = y * scale + canvasOffsetY

        val wristL = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val wristR = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        val hitL = checkHit(playerId, wristL, ::getScreenX, ::getScreenY)
        val hitR = checkHit(playerId, wristR, ::getScreenX, ::getScreenY)
        
        // Enhanced visual feedback for hits and breaks
        enhanceVisualFeedback(hitL.first || hitR.first, hitL.second || hitR.second, playerId)

        runOnUiThread {
            overlay.updateKesatriaState(rocks, p1DestroyedCount, p2DestroyedCount, nameP1, nameP2, winner)
        }
    }

    private fun checkHit(playerId: Int, landmark: PoseLandmark?, getX: (Float) -> Float, getY: (Float) -> Float): Pair<Boolean, Boolean> {
        if (landmark == null || landmark.inFrameLikelihood < 0.5f) return Pair(false, false)
        
        val x = getX(landmark.position.x)
        val y = getY(landmark.position.y)
        val lastY = lastHandY[playerId] ?: y
        
        var isHit = false
        var isBreak = false
        
        // Find rock to hit
        val rockIterator = rocks.iterator()
        while (rockIterator.hasNext()) {
            val rock = rockIterator.next()
            if (!rock.isDestroyed) {
                val isP1Area = rock.rect.centerX() < binding.overlayView.width / 2f
                if ((playerId == 1 && isP1Area) || (playerId == 2 && !isP1Area)) {
                    val hitZone = RectF(rock.rect.left - 40f, rock.rect.top - 50f, rock.rect.right + 40f, rock.rect.bottom + 40f)
                    
                    val velocityY = y - lastY
                    val isSwingingDown = velocityY > 25f // Must be moving down fast enough
                    val startedFromAbove = lastY < rock.rect.top + (rock.rect.height() / 2f) // Must come from the top
                    
                    if (hitZone.contains(x, y) && isSwingingDown && startedFromAbove) {
                        rock.hits++
                        isHit = true
                        
                        // Trigger visuals on UI thread
                        val rockRectCopy = RectF(rock.rect)
                        runOnUiThread { binding.overlayView.onRockHit(rockRectCopy) }
                        
                        if (soundIdAction != 0) soundPool.play(soundIdAction, 1f, 1f, 0, 0, 1.2f + (rock.hits * 0.1f)) // Pitch goes up
                        
                        // Shake the rock heavily to feel like a solid mass
                        rock.shakeAmount = 25f
                        
                        if (rock.hits >= 4) {
                            rock.isDestroyed = true
                            isBreak = true
                            
                            runOnUiThread { binding.overlayView.onRockDestroyed(rockRectCopy) }
                            
                            // Increase score
                            if (playerId == 1) p1DestroyedCount++ else p2DestroyedCount++
                            
                            if (soundIdAction != 0) soundPool.play(soundIdAction, 1f, 1f, 1, 0, 0.8f) 
                            
                            // Schedule removal (do NOT respawn in this mode)
                            Handler(Looper.getMainLooper()).postDelayed({
                                rocks.remove(rock)
                            }, 100)
                            
                            checkWinner()
                            if (winner != null && soundIdVictory != 0) {
                                soundPool.play(soundIdVictory, 1f, 1f, 2, 0, 1f)
                            }
                        }
                        break // Only hit one rock per frame
                    }
                }
            }
        }
        
        // Always update last hand Y so we track real movement
        lastHandY[playerId] = y
        
        return Pair(isHit, isBreak)
    }

    private fun checkWinner() {
        if (p1DestroyedCount >= totalRocksGoal) {
            winner = nameP1
        } else if (p2DestroyedCount >= totalRocksGoal) {
            winner = nameP2
        }
        
        if (winner != null) {
            runOnUiThread {
                binding.gameOverLayout.visibility = View.VISIBLE
                binding.winnerText.text = "$winner WINS!"
                
                binding.btnRetry.setOnClickListener {
                    p1DestroyedCount = 0
                    p2DestroyedCount = 0
                    winner = null
                    isGameStarted = false
                    binding.gameOverLayout.visibility = View.GONE
                    initRocks(binding.overlayView.width, binding.overlayView.height)
                    startCountdown() // Restart the countdown without the tutorial video
                }
                
                binding.btnMainMenu.setOnClickListener {
                    finish() // Close activity, returns to menu
                }
            }
        }
    }
    
    // Enhanced visual feedback for hits and breaks
    private fun enhanceVisualFeedback(isHit: Boolean, isBreak: Boolean, playerId: Int) {
        val handler = Handler(Looper.getMainLooper())
        val overlay = binding.overlayView
        
        if (isHit) {
            // Brief flash when hit
            overlay.post {
                overlay.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.BLUE)
                handler.postDelayed({
                    overlay.backgroundTintList = null
                }, 100)
            }
        }
        
        if (isBreak) {
            // Longer flash when rock breaks
            overlay.post {
                overlay.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.CYAN)
                handler.postDelayed({
                    overlay.backgroundTintList = null
                }, 300)
            }
        }
    }
    
    private fun showInitialTooltips() {
        val tooltipManager = TooltipManager(this)
        
        // Show tooltip for player 1 area
        if (tooltipManager.shouldShowTooltip("player1_area")) {
            // Position at left side of overlay, vertically centered
            val pointX = (binding.overlayView.width / 4).toInt() // 1/4 of width (left side)
            val pointY = (binding.overlayView.height / 2).toInt() // Vertically centered
            
            TooltipHelper.showAtPoint(
                this,
                binding.overlayView,
                getString(R.string.tooltip_player1_area),
                pointX,
                pointY
            )
            tooltipManager.markTooltipAsSeen("player1_area")
        }
        
        // Show tooltip for player 2 area
        if (tooltipManager.shouldShowTooltip("player2_area")) {
            // Position at right side of overlay, vertically centered
            val pointX = (binding.overlayView.width * 3 / 4).toInt() // 3/4 of width (right side)
            val pointY = (binding.overlayView.height / 2).toInt() // Vertically centered
            
            TooltipHelper.showAtPoint(
                this,
                binding.overlayView,
                getString(R.string.tooltip_player2_area),
                pointX,
                pointY
            )
            tooltipManager.markTooltipAsSeen("player2_area")
        }
    }
}