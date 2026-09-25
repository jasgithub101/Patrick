package com.patrick.faceid

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.faceid.face.BitmapImages
import com.patrick.faceid.face.FaceQualityChecker
import com.patrick.faceid.face.MlKitFaceDetector
import com.patrick.faceid.face.QualityAssessment
import com.patrick.faceid.face.QualityThresholds
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Measures what the quality metrics actually look like on genuine face photos, with the gates set
 * so permissive that nothing is rejected.
 *
 * This exists because the first threshold guesses rejected 16 percent of ordinary LFW photos as
 * blurred. Thresholds must come from a measured distribution, not from intuition. Output is logged
 * under Phase1Survey and copied into docs/PHASE_1_REPORT.md.
 */
class QualityMetricsSurveyTest {

    companion object {
        private const val TAG = "Phase1Survey"

        private lateinit var evalSet: EvalSet
        private lateinit var detector: MlKitFaceDetector

        @BeforeClass @JvmStatic fun setUp() {
            val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
            assumeTrue("eval set missing", EvalSet.isAvailable(testAssets))
            evalSet = EvalSet(testAssets)
            detector = MlKitFaceDetector()
        }

        @AfterClass @JvmStatic fun tearDown() {
            if (::detector.isInitialized) detector.close()
        }
    }

    private fun report(label: String, values: List<Float>) {
        if (values.isEmpty()) {
            Log.i(TAG, label + ": no values")
            return
        }
        val s = values.sorted()
        fun at(p: Double) = s[((s.size - 1) * p).toInt()]
        Log.i(TAG, label + " n=" + s.size +
            " min=" + at(0.0) + " p1=" + at(0.01) + " p5=" + at(0.05) +
            " p25=" + at(0.25) + " median=" + at(0.5) + " p75=" + at(0.75) +
            " max=" + at(1.0))
    }

    @Test fun surveyMetricsOnGenuineFaces() {
        // Thresholds wide open, so every detected face is accepted and measured.
        val permissive = FaceQualityChecker(
            QualityThresholds(
                minInterOcularPx = 1f,
                minBlurVariance = 0f,
                minMeanLuminance = 0f,
                maxMeanLuminance = 255f,
                maxClippedFraction = 1f,
                maxSecondFaceAreaRatio = 1f,
                maxAbsYaw = 180f,
                maxAbsPitch = 180f,
                maxAbsRoll = 180f,
                minAcceptableScore = 0f,
            )
        )
        val interOcular = mutableListOf<Float>()
        val blur = mutableListOf<Float>()
        val luminance = mutableListOf<Float>()
        val clipped = mutableListOf<Float>()
        val yaw = mutableListOf<Float>()
        val pitch = mutableListOf<Float>()
        val roll = mutableListOf<Float>()
        var faceCounts = mutableMapOf<Int, Int>()
        var noFace = 0

        for (item in evalSet.items) {
            val image = BitmapImages.decode(evalSet.bytes(item))
            val faces = detector.detect(image)
            faceCounts[faces.size] = (faceCounts[faces.size] ?: 0) + 1
            when (val a = permissive.assess(image, faces)) {
                is QualityAssessment.Accepted -> {
                    interOcular += a.metrics.interOcularPx
                    blur += a.metrics.blurVariance
                    luminance += a.metrics.meanLuminance
                    clipped += a.metrics.clippedFraction
                    a.metrics.yaw?.let { yaw += kotlin.math.abs(it) }
                    a.metrics.pitch?.let { pitch += kotlin.math.abs(it) }
                    a.metrics.roll?.let { roll += kotlin.math.abs(it) }
                }
                is QualityAssessment.Rejected -> {
                    noFace++
                    Log.w(TAG, "not measurable: " + item.path + " " + a.issue)
                }
            }
        }

        Log.i(TAG, "images=" + evalSet.items.size + " measured=" + blur.size +
            " unmeasurable=" + noFace + " facesPerImage=" + faceCounts.toSortedMap())
        report("interOcularPx ", interOcular)
        report("blurVariance  ", blur)
        report("meanLuminance ", luminance)
        report("clippedFrac   ", clipped)
        report("absYawDeg     ", yaw)
        report("absPitchDeg   ", pitch)
        report("absRollDeg    ", roll)
        Log.i(TAG, "Set each floor BELOW the p1/p5 of genuine faces so ordinary photos are not rejected.")
        assertTrue("survey should measure something", blur.isNotEmpty())
    }
}
