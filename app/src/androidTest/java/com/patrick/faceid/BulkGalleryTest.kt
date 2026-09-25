package com.patrick.faceid

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.faceid.bulk.BulkEnroller
import com.patrick.faceid.db.AppDatabase
import com.patrick.faceid.db.EmbeddingCodec
import com.patrick.faceid.db.FaceRepository
import com.patrick.faceid.face.ArcFaceOnnxEmbedder
import com.patrick.faceid.face.BitmapImages
import com.patrick.faceid.face.FaceQualityChecker
import com.patrick.faceid.face.MlKitFaceDetector
import com.patrick.faceid.face.PipelineResult
import com.patrick.faceid.face.RecognitionPipeline
import com.patrick.faceid.match.Aggregation
import com.patrick.faceid.match.BruteForceCosineMatcher
import com.patrick.faceid.match.DecisionThresholds
import com.patrick.faceid.match.EmbeddingGallery
import com.patrick.faceid.match.Outcome
import com.patrick.faceid.match.RecognitionDecisionEngine
import com.patrick.faceid.registration.RegistrationConfig
import com.patrick.faceid.registration.RegistrationService
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * M5: build a ~100-person gallery through the real pipeline, then measure recognition and search
 * at that scale.
 *
 * Needs the dataset pushed to the device first:
 *   tools/prepare_dataset.py, then adb push tools/dataset_out/bulk <externalFiles>/bulk
 *
 * Results are logged under Phase1Bulk and copied into docs/PHASE_1_REPORT.md.
 */
class BulkGalleryTest {

    companion object {
        private const val TAG = "Phase1Bulk"
        private const val DB_NAME = "bulk-test.db"

        private lateinit var db: AppDatabase
        private lateinit var repository: FaceRepository
        private lateinit var pipeline: RecognitionPipeline
        private lateinit var detector: MlKitFaceDetector
        private lateinit var embedder: ArcFaceOnnxEmbedder
        private lateinit var assets: android.content.res.AssetManager
        private lateinit var report: BulkEnroller.Report

        @BeforeClass @JvmStatic fun enrolEveryone() { runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assets = InstrumentationRegistry.getInstrumentation().context.assets
            assumeTrue(
                "bulk dataset missing - run tools/prepare_dataset.py then rebuild",
                runCatching { assets.list("bulk/enroll")?.isNotEmpty() == true }.getOrDefault(false),
            )

            context.deleteDatabase(DB_NAME)
            db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME).build()
            repository = FaceRepository(db)

            OrtEnvironment.getEnvironment()
            embedder = ArcFaceOnnxEmbedder(
                context.assets.open("models/w600k_mbf.onnx").use { it.readBytes() }
            )
            detector = MlKitFaceDetector()
            pipeline = RecognitionPipeline(detector, embedder, FaceQualityChecker())

            val enroller = BulkEnroller(
                pipeline = pipeline,
                registration = RegistrationService(repository, RegistrationConfig()),
                modelVersion = embedder.modelVersion,
                dim = embedder.dim,
            )
            report = enroller.enrol(
                source = AssetImageSource(assets, "bulk/enroll"),
                decode = { BitmapImages.decode(it) },
                onProgress = { done, total, name ->
                    if (done % 20 == 0) Log.i(TAG, "enrolled " + done + "/" + total + " (" + name + ")")
                },
            )
            Log.i(TAG, "ENROLMENT " + report)
        } }

        @AfterClass @JvmStatic fun tearDown() {
            if (::detector.isInitialized) detector.close()
            if (::embedder.isInitialized) embedder.close()
            if (::db.isInitialized) db.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB_NAME)
        }

        fun nameToPersonId(): Map<String, Long> = runBlocking {
            repository.allPeople().associate { it.displayName to it.personId }
        }

        fun median(v: List<Long>): Long = if (v.isEmpty()) -1 else v.sorted()[v.size / 2]
        fun p95(v: List<Long>): Long = if (v.isEmpty()) -1 else v.sorted()[((v.size - 1) * 0.95).toInt()]
        fun fmt(x: Float): String = String.format("%.3f", x)
        fun fmt(x: Double): String = String.format("%.3f", x)
    }

    @Test fun aGalleryOfAboutOneHundredPeopleIsBuiltFromRealImages() {
        Log.i(TAG, "people registered " + report.peopleRegistered +
            ", flagged duplicate " + report.peopleFlaggedAsDuplicate +
            ", no usable image " + report.peopleWithNoUsableImage)
        Log.i(TAG, "images " + report.imagesProcessed + ", rejected " + report.imagesRejected +
            " " + report.rejectionsByIssue)
        Log.i(TAG, "embeddings stored " + report.embeddingsStored +
            ", enrolment wall time " + report.totalMs + " ms, pipeline " + report.pipelineMs + " ms")
        report.duplicates.forEach { (name, score) ->
            Log.w(TAG, "FALSE DUPLICATE: " + name + " at " + fmt(score))
        }
        assertTrue(
            "expected a substantial gallery, got " + report.peopleRegistered,
            report.peopleRegistered >= 80,
        )
    }

    @Test fun distinctPeopleAreNotFlaggedAsDuplicatesOfEachOther() {
        // Every folder is a different LFW identity, so any duplicate warning here is a false one.
        // This measures the duplicate threshold against 100 genuinely different people.
        val rate = report.peopleFlaggedAsDuplicate.toFloat() /
            (report.peopleRegistered + report.peopleFlaggedAsDuplicate)
        Log.i(TAG, "false duplicate rate " + fmt(rate) + " (" + report.peopleFlaggedAsDuplicate +
            " of " + (report.peopleRegistered + report.peopleFlaggedAsDuplicate) + ")")
        assertTrue("too many false duplicate warnings: " + fmt(rate), rate <= 0.10f)
    }

    @Test fun heldOutProbesAreRecognisedAgainstTheFullGallery() = runBlocking {
        val gallery = repository.loadGallery(embedder.modelVersion, embedder.dim)
        val ids = nameToPersonId()
        val matcher = BruteForceCosineMatcher(Aggregation.MAX)
        val engine = RecognitionDecisionEngine(
            DecisionThresholds(matchThreshold = 0.35f, marginThreshold = 0.10f, minQuality = 0.10f)
        )

        var evaluated = 0
        var rank1 = 0
        var correctMatch = 0
        var wrongMatch = 0
        val outcomes = mutableMapOf<Outcome, Int>()
        val searchMs = mutableListOf<Long>()
        val wrongDetail = mutableListOf<String>()

        val probes = AssetImageSource(assets, "bulk/probe")
        for (personName in probes.peopleNames()) {
            val expectedId = ids[personName] ?: continue  // flagged duplicate, never registered
            for (imageBytes in probes.imagesFor(personName)) {
                val result = pipeline.process(BitmapImages.decode(imageBytes))
                if (result !is PipelineResult.Embedded) continue
                evaluated++

                val t0 = System.nanoTime()
                val scores = matcher.scorePersons(result.embedding, result.modelVersion, gallery)
                searchMs += (System.nanoTime() - t0) / 1_000_000

                val ranked = scores.sortedByDescending { it.score }
                if (ranked.firstOrNull()?.personId == expectedId) rank1++
                val decision = engine.decide(scores, result.quality)
                outcomes[decision.outcome] = (outcomes[decision.outcome] ?: 0) + 1
                if (decision.outcome == Outcome.MATCH) {
                    if (decision.best?.personId == expectedId) {
                        correctMatch++
                    } else {
                        wrongMatch++
                        wrongDetail += personName + " -> person " + decision.best?.personId +
                            " at " + fmt(decision.best?.score ?: 0f)
                    }
                }
            }
        }

        Log.i(TAG, "PROBES gallery=" + gallery.size + " embeddings, " + ids.size + " people")
        Log.i(TAG, "probes evaluated " + evaluated + ", rank-1 " + rank1 +
            " (" + fmt(rank1.toFloat() / evaluated) + ")")
        Log.i(TAG, "outcomes " + outcomes + "; correct MATCH " + correctMatch +
            ", WRONG MATCH " + wrongMatch)
        wrongDetail.forEach { Log.e(TAG, "WRONG MATCH " + it) }
        Log.i(TAG, "search ms over full gallery: median " + median(searchMs) +
            " p95 " + p95(searchMs) + " n " + searchMs.size)
        assertTrue("no probes evaluated", evaluated > 0)
        assertTrue("wrong matches must stay rare, got " + wrongMatch + "/" + evaluated,
            wrongMatch <= evaluated * 0.05)
    }

    @Test fun peopleWhoWereNeverRegisteredAreRejected() = runBlocking {
        val gallery = repository.loadGallery(embedder.modelVersion, embedder.dim)
        val matcher = BruteForceCosineMatcher(Aggregation.MAX)
        val engine = RecognitionDecisionEngine(
            DecisionThresholds(matchThreshold = 0.35f, marginThreshold = 0.10f, minQuality = 0.10f)
        )
        val outcomes = mutableMapOf<Outcome, Int>()
        val bestScores = mutableListOf<Float>()
        var falseAccepts = 0

        val strangers = AssetImageSource(assets, "bulk/unknown")
        for (personName in strangers.peopleNames()) {
            for (imageBytes in strangers.imagesFor(personName)) {
                val result = pipeline.process(BitmapImages.decode(imageBytes))
                if (result !is PipelineResult.Embedded) continue
                val decision = engine.decide(
                    matcher.scorePersons(result.embedding, result.modelVersion, gallery),
                    result.quality,
                )
                outcomes[decision.outcome] = (outcomes[decision.outcome] ?: 0) + 1
                decision.best?.let { bestScores += it.score }
                if (decision.outcome == Outcome.MATCH) {
                    falseAccepts++
                    Log.e(TAG, "FALSE ACCEPT " + personName + " -> person " +
                        decision.best?.personId + " at " + fmt(decision.best?.score ?: 0f))
                }
            }
        }

        val total = outcomes.values.sum()
        Log.i(TAG, "UNKNOWN probes " + total + ": " + outcomes)
        Log.i(TAG, "false accepts " + falseAccepts + " of " + total +
            "; their best gallery score max " + fmt(bestScores.maxOrNull() ?: 0f) +
            " mean " + fmt(bestScores.average()))
        assertTrue("strangers must not be matched, got " + falseAccepts, falseAccepts == 0)
    }

    @Test fun zSearchLatencyAgainstGallerySize() { runBlocking {
        // How search time grows with gallery size. Sub-galleries are cut from the REAL stored rows,
        // so these are genuine embeddings rather than random numbers.
        val rows = db.embeddingDao().galleryFor(embedder.modelVersion).filter { it.dim == embedder.dim }
        val probes = AssetImageSource(assets, "bulk/probe")
        val probeBytes = probes.peopleNames().firstNotNullOfOrNull { probes.imagesFor(it).firstOrNull() }
        assertTrue("need a probe image", probeBytes != null)
        val embedded = pipeline.process(BitmapImages.decode(probeBytes!!))
        assertTrue("probe must embed", embedded is PipelineResult.Embedded)
        val query = (embedded as PipelineResult.Embedded).embedding

        val matcher = BruteForceCosineMatcher(Aggregation.MAX)
        Log.i(TAG, "SEARCH SCALING (exact brute-force cosine, microseconds per search)")
        for (n in listOf(50, 100, 200, 350, rows.size).distinct().filter { it in 1..rows.size }) {
            val ids = LongArray(n)
            val vectors = FloatArray(n * embedder.dim)
            for (i in 0 until n) {
                ids[i] = rows[i].personId
                EmbeddingCodec.decode(rows[i].vector).copyInto(vectors, i * embedder.dim)
            }
            val sub = EmbeddingGallery(embedder.modelVersion, embedder.dim, ids, vectors)
            repeat(5) { matcher.scorePersons(query, embedder.modelVersion, sub) }
            val runs = 50
            val startedAt = System.nanoTime()
            repeat(runs) { matcher.scorePersons(query, embedder.modelVersion, sub) }
            val microseconds = (System.nanoTime() - startedAt) / 1000 / runs
            Log.i(TAG, "  " + n + " embeddings -> " + microseconds + " us per search")
        }
        Log.i(TAG, "NOTE emulator figures on an x86 host, not phone latency")
    } }

    @Test fun zDatabaseSizeOnDisk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.getDatabasePath(DB_NAME)
        val bytes = file.length()
        val people = report.peopleRegistered
        Log.i(TAG, "database " + bytes + " bytes for " + people + " people / " +
            report.embeddingsStored + " embeddings")
        if (people > 0) {
            Log.i(TAG, "  about " + (bytes / people) + " bytes per person on disk")
        }
        assertTrue("database should exist", bytes > 0)
    }
}
