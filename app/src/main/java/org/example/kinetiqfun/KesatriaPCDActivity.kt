package org.example.kinetiqfun

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showNameInputDialog()
        
        // Show tooltips for first-time players
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                showInitialTooltips()
            }
        })
    }

    private fun showNameInputDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_name_input, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<Button>(R.id.btnStartGame).setOnClickListener {
            val p1 = view.findViewById<EditText>(R.id.editP1).text.toString()
            val p2 = view.findViewById<EditText>(R.id.editP2).text.toString()
            nameP1 = if (p1.isNotEmpty()) p1 else "Player 1"
            nameP2 = if (p2.isNotEmpty()) p2 else "Player 2"
            isGameStarted = true
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun initRocks(width: Int, height: Int) {
        rocks.clear()
        val rockWidth = width / 10f
        val rockHeight = rockWidth * 1.1f

        // Player 1 Rocks - Falling in the center of P1's side (0.25w)
        for (i in 0 until 4) {
            val centerX = width * 0.25f
            val startY = -rockHeight - (i * 600f) 
            rocks.add(OverlayView.Rock(id = i, rect = RectF(centerX - rockWidth/2, startY, centerX + rockWidth/2, startY + rockHeight)))
        }
        // Player 2 Rocks - Falling in the center of P2's side (0.75w)
        for (i in 0 until 4) {
            val centerX = width * 0.75f
            val startY = -rockHeight - (i * 600f)
            rocks.add(OverlayView.Rock(id = i + 4, rect = RectF(centerX - rockWidth/2, startY, centerX + rockWidth/2, startY + rockHeight)))
        }
        isInitialized = true
    }

    override fun onPoseDetected(playerId: Int, pose: Pose, pXOffset: Float, imgWidth: Int, imgHeight: Int) {
        if (!isGameStarted || winner != null) return
        
        val overlay = binding.overlayView
        if (overlay.width == 0) return

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
            overlay.updateKesatriaState(rocks, nameP1, nameP2, winner)
        }
    }

    private fun checkHit(playerId: Int, landmark: PoseLandmark?, getX: (Float) -> Float, getY: (Float) -> Float): Pair<Boolean, Boolean> {
        if (landmark == null || landmark.inFrameLikelihood < 0.5f) return Pair(false, false)
        
        val x = getX(landmark.position.x)
        val y = getY(landmark.position.y)
        val lastY = lastHandY[playerId] ?: y
        
        var isHit = false
        var isBreak = false
        
        rocks.forEach { rock ->
            if (!rock.isDestroyed) {
                val isP1Area = rock.rect.centerX() < binding.overlayView.width / 2f
                if ((playerId == 1 && isP1Area) || (playerId == 2 && !isP1Area)) {
                    val hitZone = RectF(rock.rect.left, rock.rect.top - 120f, rock.rect.right, rock.rect.top + 60f)
                    if (hitZone.contains(x, y)) {
                        if (y > lastY + 15f && lastY < rock.rect.top) {
                            rock.hits++
                            isHit = true
                            if (soundIdAction != 0) soundPool.play(soundIdAction, 1f, 1f, 0, 0, 1.2f)
                            if (rock.hits >= 4) {
                                rock.isDestroyed = true
                                isBreak = true
                                // Deeper sound for break
                                if (soundIdAction != 0) soundPool.play(soundIdAction, 1f, 1f, 1, 0, 0.8f) 
                                checkWinner()
                                if (winner != null && soundIdVictory != 0) {
                                    soundPool.play(soundIdVictory, 1f, 1f, 2, 0, 1f)
                                }
                            }
                            lastHandY[playerId] = y + 1000f // Cooldown
                        }
                    } else {
                        lastHandY[playerId] = y
                    }
                }
            }
        }
        
        return Pair(isHit, isBreak)
    }

    private fun checkWinner() {
        val p1Rocks = rocks.filter { it.rect.centerX() < binding.overlayView.width / 2f }
        val p2Rocks = rocks.filter { it.rect.centerX() >= binding.overlayView.width / 2f }
        
        if (p1Rocks.all { it.isDestroyed }) {
            winner = nameP1
        } else if (p2Rocks.all { it.isDestroyed }) {
            winner = nameP2
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
        
        // Show tooltip for camera switch button
        if (tooltipManager.shouldShowTooltip("camera_switch")) {
            TooltipHelper.show(
                this,
                binding.cameraSwitchButton,
                getString(R.string.tooltip_camera_switch),
                Gravity.BOTTOM
            )
            tooltipManager.markTooltipAsSeen("camera_switch")
        }
        
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