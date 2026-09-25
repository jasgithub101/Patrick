package com.patrick.faceid.db

import androidx.room.withTransaction
import com.patrick.faceid.match.EmbeddingGallery

/**
 * The storage boundary for the recognition pipeline: everything above it works with plain
 * embeddings and a gallery, and never touches Room types.
 */
class FaceRepository(private val db: AppDatabase) {

    data class StoredEmbedding(val vector: FloatArray, val captureRole: String)

    /**
     * Create a person and store all their embeddings in ONE transaction, so an interrupted
     * registration cannot leave a person with no embeddings.
     *
     * @return the new personId
     */
    suspend fun registerPerson(
        displayName: String,
        dummyAbhaId: String,
        embeddings: List<StoredEmbedding>,
        modelVersion: String,
        dim: Int,
        now: Long = System.currentTimeMillis(),
    ): Long {
        require(embeddings.isNotEmpty()) { "a person must be registered with at least one embedding" }
        embeddings.forEach {
            require(it.vector.size == dim) { "embedding has ${it.vector.size} values, expected $dim" }
        }
        return db.withTransaction {
            val personId = db.personDao().insert(
                PersonEntity(dummyAbhaId = dummyAbhaId, displayName = displayName, createdAt = now)
            )
            db.embeddingDao().insertAll(
                embeddings.map {
                    EmbeddingEntity(
                        personId = personId,
                        vector = EmbeddingCodec.encode(it.vector),
                        modelVersion = modelVersion,
                        dim = dim,
                        captureRole = it.captureRole,
                        createdAt = now,
                    )
                }
            )
            db.auditDao().insert(
                AuditEntity(
                    timestamp = now,
                    operation = "REGISTER",
                    outcome = "CREATED",
                    personId = personId,
                    details = "embeddings=${embeddings.size} model=$modelVersion",
                )
            )
            personId
        }
    }

    /**
     * Load every stored embedding for [modelVersion] into an in-memory gallery.
     *
     * Rows recorded with a different [dim] are skipped rather than silently reshaped; that would
     * mean a stale or mismatched model version.
     */
    suspend fun loadGallery(modelVersion: String, dim: Int): EmbeddingGallery {
        val rows = db.embeddingDao().galleryFor(modelVersion).filter { it.dim == dim }
        val ids = LongArray(rows.size)
        val vectors = FloatArray(rows.size * dim)
        rows.forEachIndexed { index, row ->
            ids[index] = row.personId
            EmbeddingCodec.decode(row.vector).copyInto(vectors, index * dim)
        }
        return EmbeddingGallery(modelVersion, dim, ids, vectors)
    }

    suspend fun person(personId: Long): PersonEntity? = db.personDao().byId(personId)

    suspend fun allPeople(): List<PersonEntity> = db.personDao().all()

    suspend fun personCount(): Int = db.personDao().count()

    suspend fun embeddingCount(): Int = db.embeddingDao().count()

    suspend fun embeddingCount(personId: Long): Int = db.embeddingDao().countForPerson(personId)

    suspend fun storedModelVersions(): List<String> = db.embeddingDao().modelVersions()

    suspend fun audit(operation: String, outcome: String, details: String, personId: Long? = null) {
        db.auditDao().insert(
            AuditEntity(
                timestamp = System.currentTimeMillis(),
                operation = operation,
                outcome = outcome,
                personId = personId,
                details = details,
            )
        )
    }

    suspend fun recentAudit(limit: Int = 50): List<AuditEntity> = db.auditDao().recent(limit)

    /** Tests and the debug screen only. */
    suspend fun deleteAllPeople() = db.personDao().deleteAll()
}
