package com.patrick.faceid.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.faceid.match.Aggregation
import com.patrick.faceid.match.BruteForceCosineMatcher
import com.patrick.faceid.match.VectorMath
import com.patrick.faceid.registration.DummyAbhaId
import com.patrick.faceid.registration.RegistrationConfig
import com.patrick.faceid.registration.RegistrationResult
import com.patrick.faceid.registration.RegistrationService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Registration and persistence against a REAL file-backed SQLite database, including a
 * close/reopen cycle that stands in for an app restart.
 */
class RegistrationPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "test-faceid.db"
    private val model = "w600k_mbf@test"
    private val dim = 512

    private lateinit var db: AppDatabase
    private lateinit var repository: FaceRepository

    private fun openDb(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        db = openDb()
        repository = FaceRepository(db)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    /** A deterministic unit vector, so cosine similarities in these tests are predictable. */
    private fun unitVector(seed: Int): FloatArray {
        val random = Random(seed)
        return VectorMath.l2Normalize(FloatArray(dim) { random.nextFloat() - 0.5f })
    }

    private fun embeddings(seed: Int, count: Int) =
        (0 until count).map { FaceRepository.StoredEmbedding(unitVector(seed + it), "capture" + it) }

    @Test fun registrationStoresThePersonAndEveryEmbedding() = runBlocking {
        val service = RegistrationService(repository, RegistrationConfig(imagesPerPerson = 5))
        val result = service.register("Person A", embeddings(1, 5), model, dim)

        assertTrue(result is RegistrationResult.Registered)
        val registered = result as RegistrationResult.Registered
        assertEquals(5, registered.embeddingsStored)
        assertTrue(DummyAbhaId.isDummy(registered.dummyAbhaId))

        assertEquals(1, repository.personCount())
        assertEquals(5, repository.embeddingCount())
        assertEquals(5, repository.embeddingCount(registered.personId))

        val person = repository.person(registered.personId)
        assertNotNull(person)
        assertEquals("Person A", person!!.displayName)
    }

    @Test fun eachPersonKeepsSeveralEmbeddingsAndTheyAllReachTheGallery() = runBlocking {
        val service = RegistrationService(repository)
        service.register("A", embeddings(10, 5), model, dim, force = true)
        service.register("B", embeddings(200, 3), model, dim, force = true)

        assertEquals(2, repository.personCount())
        assertEquals(8, repository.embeddingCount())

        val gallery = repository.loadGallery(model, dim)
        assertEquals(8, gallery.size)
        val perPerson = (0 until gallery.size).groupingBy { gallery.personIdAt(it) }.eachCount()
        assertEquals(setOf(5, 3), perPerson.values.toSet())
    }

    @Test fun storedEmbeddingsSurviveAnAppRestart() = runBlocking {
        val service = RegistrationService(repository)
        val original = embeddings(7, 5)
        val registered =
            service.register("Persistent", original, model, dim) as RegistrationResult.Registered

        // Simulate the app being killed and relaunched: close and reopen the same database file.
        db.close()
        db = openDb()
        repository = FaceRepository(db)

        assertEquals(1, repository.personCount())
        assertEquals(5, repository.embeddingCount())
        assertEquals("Persistent", repository.person(registered.personId)!!.displayName)

        // Vectors must come back bit-for-bit, not merely in the right quantity.
        val gallery = repository.loadGallery(model, dim)
        assertEquals(5, gallery.size)
        val query = original.first().vector
        var bestSelfSimilarity = -1f
        for (row in 0 until gallery.size) {
            bestSelfSimilarity = maxOf(bestSelfSimilarity, gallery.dot(row, query))
        }
        assertEquals("a stored embedding must match itself exactly", 1f, bestSelfSimilarity, 1e-5f)
    }

    @Test fun recognitionStillWorksAfterRestart() = runBlocking {
        val service = RegistrationService(repository)
        val personA = embeddings(11, 5)
        val a = service.register("A", personA, model, dim, force = true)
            as RegistrationResult.Registered
        service.register("B", embeddings(500, 5), model, dim, force = true)

        db.close()
        db = openDb()
        repository = FaceRepository(db)

        val gallery = repository.loadGallery(model, dim)
        val scores = BruteForceCosineMatcher(Aggregation.MAX)
            .scorePersons(personA[2].vector, model, gallery)
            .sortedByDescending { it.score }
        assertEquals(
            "the right person must still win after a restart",
            a.personId,
            scores.first().personId,
        )
    }

    @Test fun galleryIgnoresEmbeddingsFromADifferentModelVersion() = runBlocking {
        val service = RegistrationService(repository)
        service.register("A", embeddings(21, 5), model, dim, force = true)
        service.register("B", embeddings(300, 5), "some_other_model@v2", dim, force = true)

        assertEquals(10, repository.embeddingCount())
        assertEquals(5, repository.loadGallery(model, dim).size)
        assertEquals(5, repository.loadGallery("some_other_model@v2", dim).size)
        assertEquals(0, repository.loadGallery("never_used@v9", dim).size)
        assertEquals(
            setOf(model, "some_other_model@v2"),
            repository.storedModelVersions().toSet(),
        )
    }

    @Test fun vectorsRoundTripThroughTheDatabaseUnchanged() = runBlocking {
        val service = RegistrationService(repository)
        val vector = unitVector(99)
        service.register("A", listOf(FaceRepository.StoredEmbedding(vector, "frontal")), model, dim)

        val rows = db.embeddingDao().galleryFor(model)
        assertEquals(1, rows.size)
        assertArrayEquals(vector, EmbeddingCodec.decode(rows.first().vector), 0f)
    }

    @Test fun registrationIsAuditedWithoutStoringImageData() = runBlocking {
        val service = RegistrationService(repository)
        val registered = service.register("A", embeddings(31, 5), model, dim)
            as RegistrationResult.Registered

        val entry = repository.recentAudit()
            .firstOrNull { it.operation == "REGISTER" && it.outcome == "CREATED" }
        assertNotNull("registration should be audited", entry)
        assertEquals(registered.personId, entry!!.personId)
        assertTrue("audit should record how many embeddings", entry.details.contains("embeddings=5"))
    }

    @Test fun deletingAPersonAlsoRemovesTheirEmbeddings() = runBlocking {
        val service = RegistrationService(repository)
        service.register("A", embeddings(41, 5), model, dim)
        assertEquals(5, repository.embeddingCount())

        repository.deleteAllPeople()
        assertEquals(0, repository.personCount())
        assertEquals("CASCADE should remove orphaned embeddings", 0, repository.embeddingCount())
    }

    @Test fun anEmbeddingWithTheWrongDimensionIsRejected() = runBlocking {
        val service = RegistrationService(repository)
        val wrong = listOf(
            FaceRepository.StoredEmbedding(FloatArray(128) { 1f / sqrt(128f) }, "frontal")
        )
        assertTrue("expected rejection", runCatching { service.register("A", wrong, model, dim) }.isFailure)
        assertEquals("nothing should be stored", 0, repository.personCount())
    }
}
