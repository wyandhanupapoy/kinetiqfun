package org.example.kinetiqfun

import android.app.AlertDialog
import android.graphics.Color
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
import android.widget.FrameLayout
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import org.example.kinetiqfun.TooltipHelper
import org.example.kinetiqfun.TooltipManager

class GameTwoActivity : BasePoseActivity() {

    private var nameP1 = "Player 1"
    private var nameP2 = "Player 2"
    private var isGameStarted = false
    private var winner: String? = null
    
    private var scoreP1 = 0
    private var scoreP2 = 0
    
    private val targetPoses = listOf("T-POSE", "TOUCH TOES", "HANDS UP")
    private var currentPoseIdx = 0
    private var poseStartTime = System.currentTimeMillis()
    
    private var p1Progress = 0f
    private var p2Progress = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isGameStarted = true
        
        // Show tooltips for first-time players
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                showInitialTooltips()
            }
        })
    }



    override fun onPoseDetected(playerId: Int, pose: Pose, pXOffset: Float, imgWidth: Int, imgHeight: Int) {
        if (!isGameStarted || winner != null) return

        val isMatch = checkPoseMatch(pose, targetPoses[currentPoseIdx])
        
        if (playerId == 1) {
            p1Progress = if (isMatch) (p1Progress + 0.05f).coerceAtMost(1f) else (p1Progress - 0.02f).coerceAtLeast(0f)
            if (p1Progress >= 1f) {
                scoreP1++
                if (soundIdAction != 0) soundPool.play(soundIdAction, 1f, 1f, 0, 0, 1.2f)
                resetRound()
                if (scoreP1 >= 5) {
                    winner = nameP1
                    if (soundIdVictory != 0) soundPool.play(soundIdVictory, 1f, 1f, 2, 0, 1f)
                }
            }
        } else {
            p2Progress = if (isMatch) (p2Progress + 0.05f).coerceAtMost(1f) else (p2Progress - 0.02f).coerceAtLeast(0f)
            if (p2Progress >= 1f) {
                scoreP2++
                if (soundIdAction != 0) soundPool.play(soundIdAction, 1f, 1f, 0, 0, 1.2f)
                resetRound()
                if (scoreP2 >= 5) {
                    winner = nameP2
                    if (soundIdVictory != 0) soundPool.play(soundIdVictory, 1f, 1f, 2, 0, 1f)
                }
            }
        }

        runOnUiThread {
            binding.overlayView.updateDanceState(targetPoses[currentPoseIdx], p1Progress, p2Progress, scoreP1, scoreP2, nameP1, nameP2, winner)
        }
        
        // Enhanced visual feedback for pose matching
        enhanceVisualFeedback(isMatch, playerId)
    }

    private fun resetRound() {
        p1Progress = 0f
        p2Progress = 0f
        currentPoseIdx = (currentPoseIdx + 1) % targetPoses.size
    }

    private fun checkPoseMatch(pose: Pose, target: String): Boolean {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return false
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return false
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return false
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) ?: return false
        val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) ?: return false

        return when (target) {
            "T-POSE" -> {
                abs(lw.position.y - ls.position.y) < 100 && abs(rw.position.y - rs.position.y) < 100
            }
            "TOUCH TOES" -> {
                lw.position.y > lk.position.y && rw.position.y > rk.position.y
            }
            "HANDS UP" -> {
                lw.position.y < ls.position.y - 150 && rw.position.y < rs.position.y - 150
            }
            else -> false
        }
    }
    
    // Enhanced visual feedback for pose matching
    private fun enhanceVisualFeedback(isMatch: Boolean, playerId: Int) {
        val handler = Handler(Looper.getMainLooper())
        val overlay = binding.overlayView
        
        if (isMatch) {
            // Flash green when pose matches
            overlay.post {
                overlay.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GREEN)
                handler.postDelayed({
                    overlay.backgroundTintList = null
                }, 200)
            }
        } else {
            // Shake slightly when pose doesn't match
            val shakeAnim = AnimationUtils.loadAnimation(this, R.anim.shake)
            overlay.startAnimation(shakeAnim)
        }
    }
    
    private fun showInitialTooltips() {
        val tooltipManager = TooltipManager(this)
        
        // Show tooltip for target pose indicator
        if (tooltipManager.shouldShowTooltip("target_pose")) {
            // Position at top center of overlay
            val pointX = (binding.overlayView.width / 2).toInt() // Center horizontally
            val pointY = (binding.overlayView.height / 6).toInt() // Top area
            
            TooltipHelper.showAtPoint(
                this,
                binding.overlayView,
                getString(R.string.tooltip_target_pose),
                pointX,
                pointY
            )
            tooltipManager.markTooltipAsSeen("target_pose")
        }
        
        // Show tooltip for progress bars
        if (tooltipManager.shouldShowTooltip("progress_bars")) {
            // Position at middle of overlay
            val pointX = (binding.overlayView.width / 2).toInt() // Center horizontally
            val pointY = (binding.overlayView.height / 2).toInt() // Vertically centered
            
            TooltipHelper.showAtPoint(
                this,
                binding.overlayView,
                getString(R.string.tooltip_progress_bars),
                pointX,
                pointY
            )
            tooltipManager.markTooltipAsSeen("progress_bars")
        }
    }
}