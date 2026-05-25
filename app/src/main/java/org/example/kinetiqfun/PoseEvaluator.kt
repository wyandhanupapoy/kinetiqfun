package org.example.kinetiqfun

import android.graphics.PointF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2

object PoseEvaluator {

    /**
     * Extracts an array of 8 normalized bone angles (in degrees) from a Pose.
     * The angles are normalized relative to the spine angle, making it robust against tilting.
     */
    fun extractFeatures(pose: Pose): FloatArray? {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.position ?: return null
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.position ?: return null
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)?.position ?: return null
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)?.position ?: return null

        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)?.position ?: return null
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)?.position ?: return null
        val re = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)?.position ?: return null
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)?.position ?: return null

        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)?.position ?: return null
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)?.position ?: return null
        val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)?.position ?: return null
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)?.position ?: return null

        val midShoulder = PointF((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f)
        val midHip = PointF((lh.x + rh.x) / 2f, (lh.y + rh.y) / 2f)

        // Calculate spine angle (Hip to Shoulder)
        val spineAngle = Math.toDegrees(atan2((midShoulder.y - midHip.y).toDouble(), (midShoulder.x - midHip.x).toDouble())).toFloat()

        // Helper to get relative angle
        fun getRelativeAngle(start: PointF, end: PointF): Float {
            val angle = Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())).toFloat()
            // Normalize relative to spine (so spine is basically pointing up/0 deg relative)
            var rel = angle - spineAngle
            while (rel < -180f) rel += 360f
            while (rel > 180f) rel -= 360f
            return rel
        }

        return floatArrayOf(
            getRelativeAngle(ls, le), // Left Upper Arm
            getRelativeAngle(le, lw), // Left Lower Arm
            getRelativeAngle(rs, re), // Right Upper Arm
            getRelativeAngle(re, rw), // Right Lower Arm
            getRelativeAngle(lh, lk), // Left Upper Leg
            getRelativeAngle(lk, la), // Left Lower Leg
            getRelativeAngle(rh, rk), // Right Upper Leg
            getRelativeAngle(rk, ra)  // Right Lower Leg
        )
    }

    /**
     * Compares two sets of features and returns a similarity percentage (0 to 100)
     */
    fun compareFeatures(featuresA: FloatArray, featuresB: FloatArray): Float {
        if (featuresA.size != featuresB.size) return 0f
        var totalSimilarity = 0f
        
        for (i in featuresA.indices) {
            var diff = abs(featuresA[i] - featuresB[i])
            if (diff > 180f) diff = 360f - diff
            
            // Similarity for this bone: 0 degrees diff = 1.0, 180 degrees diff = 0.0
            val boneSim = 1f - (diff / 180f)
            totalSimilarity += boneSim
        }
        
        return (totalSimilarity / featuresA.size) * 100f
    }

    /**
     * Validates if a detected pose is likely a real human based on confidence scores
     * and the presence of key landmarks (shoulders, hips, and facial features).
     */
    fun isLikelyHuman(pose: Pose): Boolean {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.size < 15) return false

        // Check essential body parts with a confidence threshold
        val minConfidence = 0.65f
        
        val essentialParts = listOf(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP
        )

        for (partType in essentialParts) {
            val landmark = pose.getPoseLandmark(partType)
            if (landmark == null || landmark.inFrameLikelihood < minConfidence) {
                return false
            }
        }

        // Check if at least one facial landmark is high confidence (to ensure facing camera)
        val faceConfidence = 0.7f
        val facialParts = listOf(PoseLandmark.NOSE, PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE)
        val hasGoodFace = facialParts.any { 
            pose.getPoseLandmark(it)?.let { it.inFrameLikelihood > faceConfidence } ?: false 
        }

        if (!hasGoodFace) return false

        // Calculate average confidence for all detected landmarks
        val avgConfidence = landmarks.map { it.inFrameLikelihood }.average()
        return avgConfidence > 0.5
    }
}
