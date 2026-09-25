package com.patrick.faceid.bulk

import com.patrick.faceid.db.FaceRepository
import com.patrick.faceid.face.PipelineResult
import com.patrick.faceid.face.QualityIssue
import com.patrick.faceid.face.RecognitionPipeline
import com.patrick.faceid.face.RgbImage
import com.patrick.faceid.registration.RegistrationResult
import com.patrick.faceid.registration.RegistrationService

/**
 * Registers many people from image files on disk, for testing at a scale nobody can reach by
 * photographing people one at a time.
 *
 * It deliberately reuses the SAME [RecognitionPipeline] and [RegistrationService] as the camera
 * flow, so a gallery built this way exercises the real quality gates, alignment, embedding and
 * duplicate check. A shortcut here would make every later measurement meaningless.
 *
 * Images arrive through [ImageSource], so the same enroller works over a folder on disk, test
 * assets, or anything else, without this class knowing about files or Android.
 */
class BulkEnroller(
    private val pipeline: RecognitionPipeline,
    private val registration: RegistrationService,
    private val modelVersion: String,
    private val dim: Int,
) {

    data class Report(
        val peopleRegistered: Int,
        val peopleFlaggedAsDuplicate: Int,
        val peopleWithNoUsableImage: Int,
        val imagesProcessed: Int,
        val imagesRejected: Int,
        val rejectionsByIssue: Map<QualityIssue, Int>,
        val embeddingsStored: Int,
        val pipelineMs: Long,
        val totalMs: Long,
        /** Names flagged as possible duplicates, with the similarity that triggered it. */
        val duplicates: List<Pair<String, Float>>,
    )

    /** Where the images come from: one named group of image bytes per person. */
    interface ImageSource {
        fun peopleNames(): List<String>
        fun imagesFor(person: String): List<ByteArray>
    }

    /**
     * @param decode turns file bytes into an image; supplied by the caller so this class stays
     *   free of Android bitmap types.
     * @param onProgress called after each person, for long runs.
     */
    suspend fun enrol(
        source: ImageSource,
        decode: (ByteArray) -> RgbImage,
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): Report {
        val people = source.peopleNames().sorted()

        var registered = 0
        var flagged = 0
        var noUsableImage = 0
        var processed = 0
        var rejected = 0
        var embeddings = 0
        var pipelineMs = 0L
        val byIssue = mutableMapOf<QualityIssue, Int>()
        val duplicates = mutableListOf<Pair<String, Float>>()
        val startedAt = System.nanoTime()

        for ((index, personName) in people.withIndex()) {
            val samples = mutableListOf<FaceRepository.StoredEmbedding>()

            for (imageBytes in source.imagesFor(personName)) {
                processed++
                when (val result = pipeline.process(decode(imageBytes))) {
                    is PipelineResult.Embedded -> {
                        pipelineMs += result.timings.totalMs
                        samples += FaceRepository.StoredEmbedding(result.embedding, "bulk")
                    }

                    is PipelineResult.Rejected -> {
                        rejected++
                        pipelineMs += result.timings.totalMs
                        byIssue[result.issue] = (byIssue[result.issue] ?: 0) + 1
                    }
                }
            }

            if (samples.isEmpty()) {
                noUsableImage++
            } else {
                // Duplicate checking stays ON: among people who are genuinely different, any
                // warning here is a FALSE duplicate and worth measuring.
                val outcome = registration.register(
                    displayName = personName,
                    embeddings = samples,
                    modelVersion = modelVersion,
                    dim = dim,
                    force = false,
                )
                when (outcome) {
                    is RegistrationResult.Registered -> {
                        registered++
                        embeddings += outcome.embeddingsStored
                    }

                    is RegistrationResult.PossibleDuplicate -> {
                        flagged++
                        duplicates += personName to outcome.similarity
                    }
                }
            }
            onProgress(index + 1, people.size, personName)
        }

        return Report(
            peopleRegistered = registered,
            peopleFlaggedAsDuplicate = flagged,
            peopleWithNoUsableImage = noUsableImage,
            imagesProcessed = processed,
            imagesRejected = rejected,
            rejectionsByIssue = byIssue,
            embeddingsStored = embeddings,
            pipelineMs = pipelineMs,
            totalMs = (System.nanoTime() - startedAt) / 1_000_000,
            duplicates = duplicates,
        )
    }
}
