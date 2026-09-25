package com.patrick.faceid.registration

import com.patrick.faceid.db.FaceRepository
import com.patrick.faceid.match.Aggregation
import com.patrick.faceid.match.BruteForceCosineMatcher
import com.patrick.faceid.match.FaceMatcher
import com.patrick.faceid.match.PersonScore
import kotlin.random.Random

/** How many images a registration collects. 5 initially; 3/7/10 are Phase 2 experiments. */
data class RegistrationConfig(
    val imagesPerPerson: Int = 5,
    /**
     * Similarity above which registration WARNS that this person may already exist. Deliberately
     * stricter than the identification threshold: a false duplicate warning costs an operator one
     * confirmation, while a missed duplicate creates two records for one person.
     *
     * UNCALIBRATED. Phase 1 evidence (report E2) is 10 identities only.
     */
    val duplicateWarnThreshold: Float = 0.35f,
) {
    init {
        require(imagesPerPerson in 1..20) { "imagesPerPerson out of range: $imagesPerPerson" }
    }
}

/** Outcome of a registration attempt. */
sealed interface RegistrationResult {
    data class Registered(val personId: Long, val dummyAbhaId: String, val embeddingsStored: Int) :
        RegistrationResult

    /**
     * A strongly similar person already exists. NOTHING was written; identities are never merged
     * automatically. The operator decides whether to proceed.
     */
    data class PossibleDuplicate(
        val existingPersonId: Long,
        val existingDisplayName: String,
        val existingDummyAbhaId: String,
        val similarity: Float,
    ) : RegistrationResult
}

/**
 * Registers a person from several face embeddings, checking first whether they look like someone
 * already in the database.
 *
 * Takes embeddings, not images: detection, quality checking and alignment happen upstream, which
 * keeps this class testable and independent of the camera.
 */
class RegistrationService(
    private val repository: FaceRepository,
    private val config: RegistrationConfig = RegistrationConfig(),
    private val matcher: FaceMatcher = BruteForceCosineMatcher(Aggregation.MAX),
    private val random: Random = Random.Default,
) {

    /**
     * @param force skip the duplicate check (the operator has confirmed this is a different person)
     */
    suspend fun register(
        displayName: String,
        embeddings: List<FaceRepository.StoredEmbedding>,
        modelVersion: String,
        dim: Int,
        force: Boolean = false,
    ): RegistrationResult {
        require(embeddings.isNotEmpty()) { "registration needs at least one embedding" }

        if (!force) {
            duplicateOf(embeddings, modelVersion, dim)?.let { return it }
        }

        val dummyAbhaId = generateUnusedDummyAbhaId()
        val personId = repository.registerPerson(
            displayName = displayName,
            dummyAbhaId = dummyAbhaId,
            embeddings = embeddings,
            modelVersion = modelVersion,
            dim = dim,
        )
        return RegistrationResult.Registered(personId, dummyAbhaId, embeddings.size)
    }

    /** The strongest existing match above the warn threshold, or null. */
    private suspend fun duplicateOf(
        embeddings: List<FaceRepository.StoredEmbedding>,
        modelVersion: String,
        dim: Int,
    ): RegistrationResult.PossibleDuplicate? {
        val gallery = repository.loadGallery(modelVersion, dim)
        if (gallery.size == 0) return null

        // Score every new embedding against the gallery and keep the single strongest hit: any one
        // image resembling an existing person is enough to warrant a warning.
        var best: PersonScore? = null
        for (candidate in embeddings) {
            for (score in matcher.scorePersons(candidate.vector, modelVersion, gallery)) {
                if (best == null || score.score > best.score) best = score
            }
        }
        val top = best ?: return null
        if (top.score < config.duplicateWarnThreshold) return null

        val existing = repository.person(top.personId) ?: return null
        repository.audit(
            operation = "REGISTER",
            outcome = "POSSIBLE_DUPLICATE",
            details = "similarity=%.3f".format(top.score),
            personId = existing.personId,
        )
        return RegistrationResult.PossibleDuplicate(
            existingPersonId = existing.personId,
            existingDisplayName = existing.displayName,
            existingDummyAbhaId = existing.dummyAbhaId,
            similarity = top.score,
        )
    }

    private suspend fun generateUnusedDummyAbhaId(): String {
        repeat(10) {
            val candidate = DummyAbhaId.generate(random)
            if (repository.allPeople().none { it.dummyAbhaId == candidate }) return candidate
        }
        error("could not generate an unused dummy ABHA id")
    }
}
