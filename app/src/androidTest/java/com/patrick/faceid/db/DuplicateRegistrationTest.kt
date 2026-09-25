package com.patrick.faceid.db

import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.faceid.match.VectorMath
import com.patrick.faceid.registration.RegistrationConfig
import com.patrick.faceid.registration.RegistrationResult
import com.patrick.faceid.registration.RegistrationService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Duplicate-registration detection. A warning must be raised and NOTHING stored; identities are
 * never merged automatically.
 */
class DuplicateRegistrationTest {

    private val model = "w600k_mbf@test"
    private val dim = 512
    private lateinit var db: AppDatabase
    private lateinit var repository: FaceRepository

    @Before fun setUp() {
        db = AppDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        repository = FaceRepository(db)
    }

    @After fun tearDown() = db.close()

    private fun unitVector(seed: Int): FloatArray {
        val random = Random(seed)
        return VectorMath.l2Normalize(FloatArray(dim) { random.nextFloat() - 0.5f })
    }

    /** A unit vector whose cosine similarity to [base] is approximately [target]. */
    private fun similarTo(base: FloatArray, target: Float, seed: Int): FloatArray {
        val other = unitVector(seed)
        // Component of `other` orthogonal to `base`, so the mix has a predictable cosine.
        var dot = 0f
        for (i in base.indices) dot += base[i] * other[i]
        val orthogonal = FloatArray(dim) { other[it] - dot * base[it] }
        val normalisedOrthogonal = VectorMath.l2Normalize(orthogonal)
        val k = sqrt(1f - target * target)
        return VectorMath.l2Normalize(
            FloatArray(dim) { target * base[it] + k * normalisedOrthogonal[it] }
        )
    }

    private fun stored(vectors: List<FloatArray>) =
        vectors.mapIndexed { i, v -> FaceRepository.StoredEmbedding(v, "capture" + i) }

    @Test fun registeringTheSamePersonAgainRaisesAWarningAndStoresNothing() = runBlocking {
        val service = RegistrationService(repository, RegistrationConfig(duplicateWarnThreshold = 0.35f))
        val base = unitVector(1)
        val first = service.register("Original", stored(List(5) { similarTo(base, 0.9f, 100 + it) }), model, dim)
            as RegistrationResult.Registered

        // The same person again: different photos, still highly similar.
        val second = service.register("Same Person Again", stored(List(5) { similarTo(base, 0.85f, 200 + it) }), model, dim)

        assertTrue("should warn about a possible duplicate", second is RegistrationResult.PossibleDuplicate)
        val duplicate = second as RegistrationResult.PossibleDuplicate
        assertEquals(first.personId, duplicate.existingPersonId)
        assertEquals("Original", duplicate.existingDisplayName)
        assertTrue("similarity should be above the threshold", duplicate.similarity >= 0.35f)

        assertEquals("no second person may be created", 1, repository.personCount())
        assertEquals("no extra embeddings may be stored", 5, repository.embeddingCount())
    }

    @Test fun aDifferentPersonRegistersNormally() = runBlocking {
        val service = RegistrationService(repository, RegistrationConfig(duplicateWarnThreshold = 0.35f))
        service.register("A", stored(List(5) { similarTo(unitVector(1), 0.9f, 100 + it) }), model, dim)
        val other = service.register("B", stored(List(5) { unitVector(9000 + it) }), model, dim)

        assertTrue("an unrelated person should register", other is RegistrationResult.Registered)
        assertEquals(2, repository.personCount())
        assertEquals(10, repository.embeddingCount())
    }

    @Test fun theFirstEverRegistrationCannotBeADuplicate() = runBlocking {
        val service = RegistrationService(repository)
        val result = service.register("First", stored(List(5) { unitVector(it) }), model, dim)
        assertTrue(result is RegistrationResult.Registered)
    }

    @Test fun forceLetsTheOperatorOverrideTheWarning() = runBlocking {
        val service = RegistrationService(repository, RegistrationConfig(duplicateWarnThreshold = 0.35f))
        val base = unitVector(2)
        service.register("Twin One", stored(List(5) { similarTo(base, 0.9f, 300 + it) }), model, dim)

        val forced = service.register(
            "Twin Two", stored(List(5) { similarTo(base, 0.88f, 400 + it) }), model, dim, force = true,
        )
        assertTrue("force should bypass the duplicate check", forced is RegistrationResult.Registered)
        assertEquals("identities are separate, never merged", 2, repository.personCount())
        assertEquals(10, repository.embeddingCount())
    }

    @Test fun aPossibleDuplicateIsAudited() = runBlocking {
        val service = RegistrationService(repository, RegistrationConfig(duplicateWarnThreshold = 0.35f))
        val base = unitVector(3)
        service.register("A", stored(List(5) { similarTo(base, 0.9f, 500 + it) }), model, dim)
        service.register("A again", stored(List(5) { similarTo(base, 0.9f, 600 + it) }), model, dim)

        val entry = repository.recentAudit().firstOrNull { it.outcome == "POSSIBLE_DUPLICATE" }
        assertTrue("the duplicate warning should be audited", entry != null)
    }

    @Test fun duplicateCheckIgnoresPeopleStoredUnderAnotherModelVersion() = runBlocking {
        val service = RegistrationService(repository, RegistrationConfig(duplicateWarnThreshold = 0.35f))
        val base = unitVector(4)
        service.register("Old model", stored(List(5) { similarTo(base, 0.95f, 700 + it) }), "old_model@v1", dim)

        // Same face, but the gallery for THIS model version is empty, so no comparison is valid.
        val result = service.register("New model", stored(List(5) { similarTo(base, 0.95f, 800 + it) }), model, dim)
        assertTrue("cross-version comparison must not happen", result is RegistrationResult.Registered)
    }
}
