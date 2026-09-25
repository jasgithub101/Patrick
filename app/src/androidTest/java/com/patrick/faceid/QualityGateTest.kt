package com.patrick.faceid

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.faceid.face.ArcFaceOnnxEmbedder
import com.patrick.faceid.face.BitmapImages
import com.patrick.faceid.face.FaceQualityChecker
import com.patrick.faceid.face.MlKitFaceDetector
import com.patrick.faceid.face.PipelineResult
import com.patrick.faceid.face.QualityIssue
import com.patrick.faceid.face.RecognitionPipeline
import com.patrick.faceid.face.RgbImage
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Quality gates against REAL images: the LFW eval set for the good case, and images derived from
 * it for the bad cases (blurred, darkened, brightened, two faces, no face).
 *
 * Measurements are logged under the tag Phase1Quality and copied into docs/PHASE_1_REPORT.md.
 */
class QualityGateTest {

    companion object {
        private const val TAG = "Phase1Quality"

        private lateinit var evalSet: EvalSet
        private lateinit var pipeline: RecognitionPipeline
        private lateinit var detector: MlKitFaceDetector
        private lateinit var embedder: ArcFaceOnnxEmbedder

        @BeforeClass @JvmStatic fun setUp() {
            val appAssets = InstrumentationRegistry.getInstrumentation().targetContext.assets
            val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
            assumeTrue("eval set missing", EvalSet.isAvailable(testAssets))
            evalSet = EvalSet(testAssets)
            OrtEnvironment.getEnvironment()
            embedder = ArcFaceOnnxEmbedder(appAssets.open("models/w600k_mbf.onnx").use { it.readBytes() })
            detector = MlKitFaceDetector()
            pipeline = RecognitionPipeline(detector, embedder, FaceQualityChecker())
        }

        @AfterClass @JvmStatic fun tearDown() {
            if (::detector.isInitialized) detector.close()
            if (::embedder.isInitialized) embedder.close()
        }

        fun firstEvalImage(): RgbImage =
            BitmapImages.decode(evalSet.bytes(evalSet.byRole("enroll").first()))
    }

    /** Average each pixel with its neighbours repeatedly, which is what camera shake looks like. */
    private fun blurred(image: RgbImage, passes: Int): RgbImage {
        var argb = image.argb.copyOf()
        repeat(passes) {
            val next = argb.copyOf()
            for (y in 1 until image.height - 1) {
                for (x in 1 until image.width - 1) {
                    var r = 0
                    var g = 0
                    var b = 0
                    for (dy in -1..1) for (dx in -1..1) {
                        val p = argb[(y + dy) * image.width + (x + dx)]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                    }
                    next[y * image.width + x] = RgbImage.argb(r / 9, g / 9, b / 9)
                }
            }
            argb = next
        }
        return RgbImage(image.width, image.height, argb)
    }

    private fun scaled(image: RgbImage, factor: Float): RgbImage =
        RgbImage(image.width, image.height, IntArray(image.argb.size) { i ->
            val p = image.argb[i]
            RgbImage.argb(
                (((p shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255),
                (((p shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255),
                ((p and 0xFF) * factor).toInt().coerceIn(0, 255),
            )
        })

    /** Two faces side by side, built from two different eval images. */
    private fun twoFaces(): RgbImage {
        val items = evalSet.byRole("enroll").take(2)
        val a = BitmapImages.decode(evalSet.bytes(items[0]))
        val b = BitmapImages.decode(evalSet.bytes(items[1]))
        val width = a.width + b.width
        val height = maxOf(a.height, b.height)
        val out = IntArray(width * height)
        for (y in 0 until a.height) for (x in 0 until a.width) {
            out[y * width + x] = a.argb[y * a.width + x]
        }
        for (y in 0 until b.height) for (x in 0 until b.width) {
            out[y * width + a.width + x] = b.argb[y * b.width + x]
        }
        return RgbImage(width, height, out)
    }

    private fun rejection(result: PipelineResult): PipelineResult.Rejected {
        assertTrue("expected rejection, got " + result, result is PipelineResult.Rejected)
        return result as PipelineResult.Rejected
    }

    @Test fun allGenuineEvalImagesPassTheQualityGates() {
        var accepted = 0
        val rejections = mutableMapOf<QualityIssue, Int>()
        val scores = mutableListOf<Float>()
        for (item in evalSet.items) {
            when (val result = pipeline.process(BitmapImages.decode(evalSet.bytes(item)))) {
                is PipelineResult.Embedded -> {
                    accepted++
                    scores += result.quality
                }
                is PipelineResult.Rejected -> {
                    rejections[result.issue] = (rejections[result.issue] ?: 0) + 1
                    Log.w(TAG, "rejected " + item.path + ": " + result.issue + " " + result.message)
                }
            }
        }
        Log.i(TAG, "eval images accepted " + accepted + "/" + evalSet.items.size +
            "; rejections=" + rejections)
        Log.i(TAG, "quality score min=" + scores.minOrNull() + " mean=" + scores.average() +
            " max=" + scores.maxOrNull())
        assertTrue(
            "the gates must not reject ordinary photos: only " + accepted + " of " +
                evalSet.items.size + " passed",
            accepted >= evalSet.items.size * 0.9,
        )
    }

    @Test fun aBlankImageIsRejectedAsNoFace() {
        val blank = RgbImage(320, 320, IntArray(320 * 320) { RgbImage.argb(128, 128, 128) })
        assertEquals(QualityIssue.NO_FACE, rejection(pipeline.process(blank)).issue)
    }

    @Test fun twoPeopleInFrameAreRejected() {
        val r = rejection(pipeline.process(twoFaces()))
        assertEquals(QualityIssue.MULTIPLE_FACES, r.issue)
        Log.i(TAG, "multiple faces: " + r.message)
    }

    @Test fun aHeavilyBlurredFaceIsRejected() {
        val r = rejection(pipeline.process(blurred(firstEvalImage(), passes = 12)))
        Log.i(TAG, "blurred: " + r.issue + " blurVariance=" + r.metrics?.blurVariance)
        assertTrue(
            "a heavily blurred face should be rejected for blur, or not detected at all",
            r.issue == QualityIssue.TOO_BLURRY || r.issue == QualityIssue.NO_FACE,
        )
    }

    @Test fun aVeryDarkFaceIsRejected() {
        val r = rejection(pipeline.process(scaled(firstEvalImage(), 0.12f)))
        Log.i(TAG, "darkened: " + r.issue + " meanLuminance=" + r.metrics?.meanLuminance)
        assertTrue(
            "a very dark face should be rejected for darkness, or not detected at all",
            r.issue == QualityIssue.TOO_DARK || r.issue == QualityIssue.NO_FACE,
        )
    }

    @Test fun aBlownOutFaceIsRejected() {
        val r = rejection(pipeline.process(scaled(firstEvalImage(), 4.0f)))
        Log.i(TAG, "blown out: " + r.issue + " mean=" + r.metrics?.meanLuminance +
            " clipped=" + r.metrics?.clippedFraction)
        assertTrue(
            "an over-exposed face should be rejected for brightness, or not detected at all",
            r.issue == QualityIssue.TOO_BRIGHT || r.issue == QualityIssue.NO_FACE,
        )
    }

    @Test fun rejectedImagesAreNeverEmbedded() {
        // Rejection must short-circuit before alignment and embedding, so bad data cannot reach
        // the gallery and no inference time is wasted.
        val black = RgbImage(200, 200, IntArray(40000) { RgbImage.argb(0, 0, 0) })
        val r = rejection(pipeline.process(black))
        assertEquals(0L, r.timings.alignMs)
        assertEquals(0L, r.timings.embedMs)
    }

    @Test fun zAcceptedImagesReportEveryStageTiming() {
        val result = pipeline.process(firstEvalImage())
        assertTrue(result is PipelineResult.Embedded)
        val timings = (result as PipelineResult.Embedded).timings
        Log.i(TAG, "stage ms detect=" + timings.detectMs + " quality=" + timings.qualityMs +
            " align=" + timings.alignMs + " embed=" + timings.embedMs + " total=" + timings.totalMs)
        Log.i(TAG, "NOTE emulator figures, NOT evidence for the 2 s phone target")
        assertTrue("total latency should be recorded", timings.totalMs >= 0)
    }
}
