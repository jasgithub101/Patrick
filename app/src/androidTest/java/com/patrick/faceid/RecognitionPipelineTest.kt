package com.patrick.faceid

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.faceid.face.ArcFaceOnnxEmbedder
import com.patrick.faceid.face.BitmapImages
import com.patrick.faceid.face.FaceAligner
import com.patrick.faceid.face.MlKitFaceDetector
import com.patrick.faceid.match.Aggregation
import com.patrick.faceid.match.BruteForceCosineMatcher
import com.patrick.faceid.match.DecisionThresholds
import com.patrick.faceid.match.EmbeddingGallery
import com.patrick.faceid.match.Outcome
import com.patrick.faceid.match.RecognitionDecisionEngine
import com.patrick.faceid.match.VectorMath
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * End-to-end pipeline on REAL LFW faces: detect, align, embed, match, decide.
 *
 * A measurement, not an accuracy gate. Thresholds are uncalibrated, so assertions cover
 * structural invariants only. Measured numbers are logged under the tag Phase1Eval and copied
 * into docs/PHASE_1_REPORT.md.
 *
 * Requires: .venv/Scripts/python.exe tools/prepare_dataset.py
 */
class RecognitionPipelineTest {

    companion object {
        private const val TAG = "Phase1Eval"

        private lateinit var evalSet: EvalSet
        private lateinit var detector: MlKitFaceDetector
        private lateinit var embedder: ArcFaceOnnxEmbedder

        private val enrolled = LinkedHashMap<String, MutableList<FloatArray>>()
        private val detectMs = mutableListOf<Long>()
        private val embedMs = mutableListOf<Long>()
        private var noFaceDetected = 0

        @BeforeClass @JvmStatic fun setUp() {
            val appAssets = InstrumentationRegistry.getInstrumentation().targetContext.assets
            val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
            assumeTrue("eval set missing, run tools/prepare_dataset.py", EvalSet.isAvailable(testAssets))
            evalSet = EvalSet(testAssets)

            OrtEnvironment.getEnvironment()
            embedder = ArcFaceOnnxEmbedder(appAssets.open("models/w600k_mbf.onnx").use { it.readBytes() })
            detector = MlKitFaceDetector()

            for (item in evalSet.byRole("enroll")) {
                pipeline(item)?.let { enrolled.getOrPut(item.identity) { mutableListOf() }.add(it) }
            }
            Log.i(TAG, "source=" + evalSet.source + " model=" + embedder.modelVersion)
            Log.i(TAG, "enrolled " + enrolled.size + " identities, " +
                enrolled.values.sumOf { it.size } + " embeddings")
        }

        @AfterClass @JvmStatic fun tearDown() {
            if (::detector.isInitialized) detector.close()
            if (::embedder.isInitialized) embedder.close()
        }

        fun pipeline(item: EvalSet.Item): FloatArray? {
            val image = BitmapImages.decode(evalSet.bytes(item))
            val t0 = System.nanoTime()
            val faces = detector.detect(image)
            detectMs += (System.nanoTime() - t0) / 1_000_000
            val face = faces.firstOrNull { it.hasLandmarks }
            if (face == null) {
                noFaceDetected++
                Log.w(TAG, "no usable face: " + item.path)
                return null
            }
            val aligned = FaceAligner.align(image, face.landmarks)
            val t1 = System.nanoTime()
            val embedding = embedder.embed(aligned)
            embedMs += (System.nanoTime() - t1) / 1_000_000
            return embedding
        }

        fun gallery(): EmbeddingGallery {
            val ids = mutableListOf<Long>()
            val vectors = mutableListOf<Float>()
            enrolled.keys.forEachIndexed { index, name ->
                enrolled.getValue(name).forEach { v ->
                    ids += index.toLong()
                    vectors += v.toList()
                }
            }
            return EmbeddingGallery(embedder.modelVersion, embedder.dim,
                ids.toLongArray(), vectors.toFloatArray())
        }

        fun personIdOf(identity: String): Long = enrolled.keys.indexOf(identity).toLong()

        fun median(v: List<Long>): Long = if (v.isEmpty()) -1 else v.sorted()[v.size / 2]

        fun p95(v: List<Long>): Long = if (v.isEmpty()) -1 else v.sorted()[((v.size - 1) * 0.95).toInt()]

        fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }

        fun fmt(x: Float): String = String.format("%.3f", x)

        fun fmt(x: Double): String = String.format("%.3f", x)
    }

    @Test fun everyEnrolmentImageYieldsAValidEmbedding() {
        val expected = evalSet.byRole("enroll").size
        val actual = enrolled.values.sumOf { it.size }
        Log.i(TAG, "detection: " + actual + "/" + expected + " enrolment images embedded, " +
            noFaceDetected + " with no usable face")
        assertEquals("all 10 identities should be enrolled", 10, enrolled.size)
        assertTrue("at least 90 percent of enrolment images should yield a face",
            actual >= expected * 0.9)
        enrolled.values.flatten().forEach { v ->
            assertEquals(512, v.size)
            assertEquals(1f, VectorMath.norm(v, 0, v.size), 1e-3f)
        }
    }

    @Test fun samePersonSimilarityExceedsDifferentPersonSimilarity() {
        val same = mutableListOf<Float>()
        val diff = mutableListOf<Float>()
        val names = enrolled.keys.toList()
        for (i in names.indices) {
            val a = enrolled.getValue(names[i])
            for (x in a.indices) for (y in x + 1 until a.size) same += dot(a[x], a[y])
            for (j in i + 1 until names.size) {
                for (p in a) for (q in enrolled.getValue(names[j])) diff += dot(p, q)
            }
        }
        listOf("same-person" to same, "diff-person" to diff).forEach { (label, v) ->
            val s = v.sorted()
            Log.i(TAG, label + " n=" + v.size +
                " min=" + fmt(s.first()) +
                " p5=" + fmt(s[(s.size * 0.05).toInt()]) +
                " mean=" + fmt(v.average()) +
                " p95=" + fmt(s[(s.size * 0.95).toInt()]) +
                " max=" + fmt(s.last()))
        }
        assertTrue("same-person similarity must exceed different-person similarity",
            same.average() > diff.average())
    }

    @Test fun heldOutProbesRankTheCorrectPersonFirst() {
        val gallery = gallery()
        val matcher = BruteForceCosineMatcher(Aggregation.MAX)
        var rank1 = 0
        var evaluated = 0
        val margins = mutableListOf<Float>()
        for (item in evalSet.byRole("probe")) {
            val q = pipeline(item) ?: continue
            evaluated++
            val scores = matcher.scorePersons(q, embedder.modelVersion, gallery)
                .sortedByDescending { it.score }
            val best = scores.first()
            val runnerUp = scores.getOrNull(1)
            if (best.personId == personIdOf(item.identity)) rank1++
            else Log.w(TAG, "rank-1 miss: " + item.identity + " scored as person " +
                best.personId + " at " + fmt(best.score))
            margins += best.score - (runnerUp?.score ?: 0f)
            Log.i(TAG, "probe " + item.identity + " best=" + fmt(best.score) +
                " runnerUp=" + fmt(runnerUp?.score ?: 0f))
        }
        Log.i(TAG, "rank-1 " + rank1 + "/" + evaluated +
            "; margin mean=" + fmt(margins.average()) + " min=" + fmt(margins.minOrNull() ?: 0f))
        assertTrue("at least one probe must be evaluated", evaluated > 0)
        assertTrue("correct person should rank first for most probes", rank1 >= evaluated * 0.8)
    }

    @Test fun neverEnrolledPeopleAreNotMatched() {
        val gallery = gallery()
        val matcher = BruteForceCosineMatcher(Aggregation.MAX)
        // Placeholder thresholds. Recording behaviour, not asserting a calibrated operating point.
        val engine = RecognitionDecisionEngine(DecisionThresholds(0.5f, 0.10f, 0.0f))
        val outcomes = mutableMapOf<Outcome, Int>()
        val bestScores = mutableListOf<Float>()
        for (item in evalSet.byRole("unknown")) {
            val q = pipeline(item) ?: continue
            val decision = engine.decide(
                matcher.scorePersons(q, embedder.modelVersion, gallery), quality = 1f)
            outcomes[decision.outcome] = (outcomes[decision.outcome] ?: 0) + 1
            decision.best?.let { bestScores += it.score }
            if (decision.outcome == Outcome.MATCH) {
                Log.e(TAG, "FALSE MATCH: " + item.identity + " as person " +
                    decision.best?.personId + " at " + fmt(decision.best?.score ?: 0f))
            }
        }
        Log.i(TAG, "unknown probes: " + outcomes +
            "; best-score max=" + fmt(bestScores.maxOrNull() ?: 0f) +
            " mean=" + fmt(bestScores.average()))
        assertEquals("never-enrolled people must not be MATCHed at this threshold",
            0, outcomes[Outcome.MATCH] ?: 0)
    }

    @Test fun zStageLatencies() {
        Log.i(TAG, "detect ms median=" + median(detectMs) + " p95=" + p95(detectMs) +
            " n=" + detectMs.size)
        Log.i(TAG, "embed ms median=" + median(embedMs) + " p95=" + p95(embedMs) +
            " n=" + embedMs.size)
        Log.i(TAG, "NOTE emulator figures on an x86 host, NOT evidence for the phone target")
        assertTrue(detectMs.isNotEmpty() && embedMs.isNotEmpty())
    }
}
