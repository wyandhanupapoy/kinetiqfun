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

class GameThreeActivity : BasePoseActivity() {

    private var nameP1 = "Player 1"
    private var nameP2 = "Player 2"
    private var isGameStarted = false
    private var winner: String? = null
    
    private var hpP1 = 100
    private var hpP2 = 100
    
    private val projectiles = mutableListOf<OverlayView.Projectile>()
    private val lastShootTime = mutableMapOf<Int, Long>()

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

    override fun onPoseDetected(playerId: Int, pose: Pose, pXOffset: Float, imgWidth: Int, imgHeight: Int) {
        if (!isGameStarted || winner != null) return
        
        val overlay = binding.overlayView
        if (overlay.width == 0) return

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
        val shoulderL = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val shoulderR = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        // SHOOT LOGIC: Punch forward to shoot
        val shotL = checkShoot(playerId, wristL, shoulderL, ::getScreenX, ::getScreenY)
        val shotR = checkShoot(playerId, wristR, shoulderR, ::getScreenX, ::getScreenY)

        // UPDATE PROJECTILES
        updateProjectiles(overlay.width.toFloat())
        
        // COLLISION
        val hit = checkCollisions(pose, playerId, ::getScreenX, ::getScreenY)
        
        // Enhanced visual feedback for hits and shots
        enhanceVisualFeedback(hit, playerId, shotL || shotR)

        if (hpP1 <= 0 && winner == null) {
            winner = nameP2
            if (soundIdVictory != 0) soundPool.play(soundIdVictory, 1f, 1f, 2, 0, 1f)
        }
        if (hpP2 <= 0 && winner == null) {
            winner = nameP1
            if (soundIdVictory != 0) soundPool.play(soundIdVictory, 1f, 1f, 2, 0, 1f)
        }

        runOnUiThread {
            overlay.updateFighterState(projectiles.toList(), hpP1, hpP2, nameP1, nameP2, winner)
        }
    }

    private fun checkShoot(playerId: Int, wrist: PoseLandmark?, shoulder: PoseLandmark?, getX: (Float) -> Float, getY: (Float) -> Float): Boolean {
        if (wrist == null || shoulder == null) return false
        val now = System.currentTimeMillis()
        if (now - (lastShootTime[playerId] ?: 0) < 500) return false

        // Simple punch detection: wrist far from shoulder in X direction (since they face each other)
        val dist = Math.abs(wrist.position.x - shoulder.position.x)
        if (dist > 100) {
            val vx = if (playerId == 1) 30f else -30f
            projectiles.add(OverlayView.Projectile(getX(wrist.position.x), getY(wrist.position.y), vx, playerId))
            lastShootTime[playerId] = now
            if (soundIdAction != 0) soundPool.play(soundIdAction, 1f, 1f, 0, 0, 1f)
            return true
        }
        return false
    }

    private fun updateProjectiles(width: Float) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            if (p.x < 0 || p.x > width) iterator.remove()
        }
    }

    private fun checkCollisions(pose: Pose, playerId: Int, getX: (Float) -> Float, getY: (Float) -> Float): Boolean {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: return false
        
        val bodyRect = RectF(
            Math.min(getX(ls.position.x), getX(rs.position.x)),
            getY(ls.position.y) - 50f,
            Math.max(getX(ls.position.x), getX(rs.position.x)),
            getY(lh.position.y)
        )
        
        var collisionOccurred = false
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            if (p.ownerId != playerId && bodyRect.contains(p.x, p.y)) {
                if (playerId == 1) hpP1 -= 10 else hpP2 -= 10
                iterator.remove()
                collisionOccurred = true
            }
        }
        return collisionOccurred
    }

    private fun showInitialTooltips() {
        val tooltipManager = TooltipManager(this)
        
        // Show tooltip for health bars
        if (tooltipManager.shouldShowTooltip("health_bars")) {
            // Position at top center of overlay
            val pointX = (binding.overlayView.width / 2).toInt() // Center horizontally
            val pointY = (binding.overlayView.height / 8).toInt() // Top area
            
            TooltipHelper.showAtPoint(
                this,
                binding.overlayView,
                getString(R.string.tooltip_health_bars),
                pointX,
                pointY
            )
            tooltipManager.markTooltipAsSeen("health_bars")
        }
        
        // Show tooltip for projectile indicator
        if (tooltipManager.shouldShowTooltip("projectiles")) {
            // Position at bottom center of overlay
            val pointX = (binding.overlayView.width / 2).toInt() // Center horizontally
            val pointY = (binding.overlayView.height * 7 / 8).toInt() // Bottom area
            
            TooltipHelper.showAtPoint(
                this,
                binding.overlayView,
                getString(R.string.tooltip_projectiles),
                pointX,
                pointY
            )
            tooltipManager.markTooltipAsSeen("projectiles")
        }
    }
    
    // Enhanced visual feedback for hits and shots
    private fun enhanceVisualFeedback(isHit: Boolean, playerId: Int, isShoot: Boolean) {
        val handler = Handler(Looper.getMainLooper())
        val overlay = binding.overlayView
        
        if (isHit) {
            // Flash red when hit
            overlay.post {
                overlay.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
                handler.postDelayed({
                    overlay.backgroundTintList = null
                }, 150)
            }
        }
        
        if (isShoot) {
            // Brief flash when shooting
            overlay.post {
                overlay.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.YELLOW)
                handler.postDelayed({
                    overlay.backgroundTintList = null
                }, 100)
            }
        }
    }
}