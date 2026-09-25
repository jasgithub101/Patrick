package com.patrick.faceid.app

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import com.patrick.faceid.config.RecognitionSettings
import com.patrick.faceid.config.SettingsStore
import com.patrick.faceid.db.AppDatabase
import com.patrick.faceid.db.FaceRepository
import com.patrick.faceid.face.ArcFaceOnnxEmbedder
import com.patrick.faceid.face.FaceQualityChecker
import com.patrick.faceid.face.MlKitFaceDetector
import com.patrick.faceid.face.PipelineResult
import com.patrick.faceid.face.QualityIssue
import com.patrick.faceid.face.QualityMetrics
import com.patrick.faceid.face.RecognitionPipeline
import com.patrick.faceid.face.RgbImage
import com.patrick.faceid.face.StageTimings
import com.patrick.faceid.match.Aggregation
import com.patrick.faceid.match.BruteForceCosineMatcher
import com.patrick.faceid.match.Decision
import com.patrick.faceid.match.DecisionReason
import com.patrick.faceid.match.Outcome
import com.patrick.faceid.match.RecognitionDecisionEngine
import com.patrick.faceid.registration.RegistrationResult
import com.patrick.faceid.registration.RegistrationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the identify screen shows. Every field comes from the real pipeline. */
data class IdentifyOutcome(
    val outcome: Outcome,
    val reason: DecisionReason,
    val personName: String?,
    val dummyAbhaId: String?,
    val bestSimilarity: Float?,
    val runnerUpSimilarity: Float?,
    val margin: Float?,
    val quality: Float?,
    val metrics: QualityMetrics?,
    val timings: StageTimings,
    val searchMs: Long,
    /** Set when the image was rejected before recognition could run. */
    val rejection: QualityIssue? = null,
    val message: String? = null,
) {
    val totalMs: Long get() = timings.totalMs + searchMs
}

/** Counts and model details for the technical panel. */
data class EngineStatus(
    val peopleRegistered: Int,
    val embeddingsStored: Int,
    val modelVersion: String,
    val embeddingDim: Int,
    val detectorName: String,
    val alignment: String,
    val settings: RecognitionSettings,
)

/**
 * Everything the UI needs, in one place: detection, quality, alignment, embedding, search and the
 * decision. The UI never touches ONNX, Room or the matcher directly, so the presentation layer
 * stays replaceable and no recognition logic leaks into a screen.
 *
 * Heavy work runs on Dispatchers.Default; callers are suspend functions.
 */
class RecognitionEngine private constructor(
    private val repository: FaceRepository,
    private val settingsStore: SettingsStore,
    private val detector: MlKitFaceDetector,
    private val embedder: ArcFaceOnnxEmbedder,
) {

    private val matcher = BruteForceCosineMatcher(Aggregation.MAX)

    suspend fun status(): EngineStatus = withContext(Dispatchers.IO) {
        EngineStatus(
            peopleRegistered = repository.personCount(),
            embeddingsStored = repository.embeddingCount(),
            modelVersion = embedder.modelVersion,
            embeddingDim = embedder.dim,
            detectorName = "ML Kit face-detection 16.1.7 (bundled)",
            alignment = "5-point similarity transform to ArcFace 112x112",
            settings = settingsStore.current(),
        )
    }

    suspend fun settings(): RecognitionSettings = settingsStore.current()

    /** Run the pipeline on one image, for a registration sample. */
    suspend fun captureSample(image: RgbImage): PipelineResult = withContext(Dispatchers.Default) {
        pipeline(settingsStore.current()).process(image)
    }

    /** Full identification: pipeline, then search, then the decision engine. */
    suspend fun identify(image: RgbImage): IdentifyOutcome = withContext(Dispatchers.Default) {
        val settings = settingsStore.current()
        when (val result = pipeline(settings).process(image)) {
            is PipelineResult.Rejected -> IdentifyOutcome(
                outcome = Outcome.UNCERTAIN,
                reason = DecisionReason.LOW_QUALITY,
                personName = null,
                dummyAbhaId = null,
                bestSimilarity = null,
                runnerUpSimilarity = null,
                margin = null,
                quality = null,
                metrics = result.metrics,
                timings = result.timings,
                searchMs = 0,
                rejection = result.issue,
                message = result.message,
            )

            is PipelineResult.Embedded -> {
                val searchStart = System.nanoTime()
                val gallery = repository.loadGallery(result.modelVersion, embedder.dim)
                val scores = matcher.scorePersons(result.embedding, result.modelVersion, gallery)
                val decision: Decision = RecognitionDecisionEngine(settings.decision)
                    .decide(scores, result.quality)
                val searchMs = (System.nanoTime() - searchStart) / 1_000_000

                val person = decision.best
                    ?.takeIf { decision.outcome == Outcome.MATCH }
                    ?.let { repository.person(it.personId) }

                IdentifyOutcome(
                    outcome = decision.outcome,
                    reason = decision.reason,
                    personName = person?.displayName,
                    dummyAbhaId = person?.dummyAbhaId,
                    bestSimilarity = decision.best?.score,
                    runnerUpSimilarity = decision.runnerUp?.score,
                    margin = decision.margin,
                    quality = result.quality,
                    metrics = result.metrics,
                    timings = result.timings,
                    searchMs = searchMs,
                )
            }
        }
    }

    suspend fun register(
        displayName: String,
        samples: List<FaceRepository.StoredEmbedding>,
        force: Boolean = false,
    ): RegistrationResult = withContext(Dispatchers.IO) {
        RegistrationService(repository, settingsStore.current().registration).register(
            displayName = displayName,
            embeddings = samples,
            modelVersion = embedder.modelVersion,
            dim = embedder.dim,
            force = force,
        )
    }

    suspend fun people() = withContext(Dispatchers.IO) {
        repository.allPeople().map { it to repository.embeddingCount(it.personId) }
    }

    private fun pipeline(settings: RecognitionSettings) =
        RecognitionPipeline(detector, embedder, FaceQualityChecker(settings.quality))

    companion object {
        @Volatile private var instance: RecognitionEngine? = null

        /** Loads the model from assets, so it must not run on the main thread. */
        suspend fun get(context: Context): RecognitionEngine = instance ?: withContext(Dispatchers.IO) {
            synchronized(this) { instance } ?: run {
                val app = context.applicationContext
                OrtEnvironment.getEnvironment()
                val embedder = ArcFaceOnnxEmbedder(
                    app.assets.open("models/w600k_mbf.onnx").use { it.readBytes() }
                )
                val created = RecognitionEngine(
                    repository = FaceRepository(AppDatabase.get(app)),
                    settingsStore = SettingsStore(app),
                    detector = MlKitFaceDetector(),
                    embedder = embedder,
                )
                synchronized(this) {
                    instance ?: created.also { instance = it }
                }
            }
        }
    }
}
