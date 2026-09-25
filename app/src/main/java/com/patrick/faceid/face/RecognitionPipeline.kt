package com.patrick.faceid.face

/** Per-stage timings in milliseconds, so latency is measured rather than assumed. */
data class StageTimings(
    val detectMs: Long = 0,
    val qualityMs: Long = 0,
    val alignMs: Long = 0,
    val embedMs: Long = 0,
) {
    val totalMs: Long get() = detectMs + qualityMs + alignMs + embedMs
}

/** A usable embedding with the quality that produced it, or a rejection to retry. */
sealed interface PipelineResult {

    val timings: StageTimings

    data class Embedded(
        val embedding: FloatArray,
        val modelVersion: String,
        val quality: Float,
        val metrics: QualityMetrics,
        override val timings: StageTimings,
    ) : PipelineResult

    data class Rejected(
        val issue: QualityIssue,
        val message: String,
        val metrics: QualityMetrics?,
        override val timings: StageTimings,
    ) : PipelineResult
}

/**
 * The camera-independent half of recognition: detect, quality-check, align, embed.
 *
 * Takes an [RgbImage], so it works identically for a camera frame, a file, or a test fixture.
 * Rejection short-circuits: a poor image is never aligned or embedded, which is both faster and
 * the reason bad data cannot reach the gallery.
 */
class RecognitionPipeline(
    private val detector: FaceDetector,
    private val embedder: FaceEmbeddingModel,
    private val qualityChecker: FaceQualityChecker = FaceQualityChecker(),
) {

    fun process(image: RgbImage): PipelineResult {
        val detectStart = System.nanoTime()
        val faces = detector.detect(image)
        val detectMs = elapsedMs(detectStart)

        val qualityStart = System.nanoTime()
        val assessment = qualityChecker.assess(image, faces)
        val qualityMs = elapsedMs(qualityStart)

        when (assessment) {
            is QualityAssessment.Rejected -> return PipelineResult.Rejected(
                issue = assessment.issue,
                message = assessment.message,
                metrics = assessment.metrics,
                timings = StageTimings(detectMs = detectMs, qualityMs = qualityMs),
            )

            is QualityAssessment.Accepted -> {
                val alignStart = System.nanoTime()
                val aligned = FaceAligner.align(image, assessment.face.landmarks)
                val alignMs = elapsedMs(alignStart)

                val embedStart = System.nanoTime()
                val embedding = embedder.embed(aligned)
                val embedMs = elapsedMs(embedStart)

                return PipelineResult.Embedded(
                    embedding = embedding,
                    modelVersion = embedder.modelVersion,
                    quality = assessment.score,
                    metrics = assessment.metrics,
                    timings = StageTimings(detectMs, qualityMs, alignMs, embedMs),
                )
            }
        }
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
