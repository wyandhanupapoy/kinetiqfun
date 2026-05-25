package org.example.kinetiqfun

import android.graphics.PointF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class PoseEvaluatorTest {

    @Test
    fun testCompareFeatures_PerfectMatch() {
        val featuresA = floatArrayOf(45f, 45f, 45f, 45f, 45f, 45f, 45f, 45f)
        val featuresB = floatArrayOf(45f, 45f, 45f, 45f, 45f, 45f, 45f, 45f)
        
        val similarity = PoseEvaluator.compareFeatures(featuresA, featuresB)
        assertEquals(100f, similarity, 0.01f)
    }

    @Test
    fun testCompareFeatures_OppositeMatch() {
        val featuresA = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val featuresB = floatArrayOf(180f, 180f, 180f, 180f, 180f, 180f, 180f, 180f)
        
        val similarity = PoseEvaluator.compareFeatures(featuresA, featuresB)
        assertEquals(0f, similarity, 0.01f)
    }

    @Test
    fun testCompareFeatures_WrapAround() {
        val featuresA = floatArrayOf(170f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val featuresB = floatArrayOf(-170f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        
        // Difference is 20 degrees (170 to 180, -180 to -170)
        // Similarity for one bone: 1 - (20/180) = 0.888...
        // Average for 8 bones: (0.888 + 7)/8 = 0.9861...
        val similarity = PoseEvaluator.compareFeatures(featuresA, featuresB)
        assertEquals(98.61f, similarity, 0.01f)
    }
}
