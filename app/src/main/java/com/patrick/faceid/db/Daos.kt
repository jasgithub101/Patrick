package com.patrick.faceid.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** One gallery row: which person an embedding belongs to, and the embedding itself. */
data class GalleryRow(val personId: Long, val vector: ByteArray, val dim: Int)

@Dao
interface PersonDao {
    @Insert suspend fun insert(person: PersonEntity): Long

    @Query("SELECT * FROM person WHERE personId = :personId")
    suspend fun byId(personId: Long): PersonEntity?

    @Query("SELECT * FROM person WHERE dummyAbhaId = :dummyAbhaId")
    suspend fun byDummyAbhaId(dummyAbhaId: String): PersonEntity?

    @Query("SELECT * FROM person ORDER BY personId")
    suspend fun all(): List<PersonEntity>

    @Query("SELECT COUNT(*) FROM person")
    suspend fun count(): Int

    @Query("DELETE FROM person")
    suspend fun deleteAll()
}

@Dao
interface EmbeddingDao {
    @Insert suspend fun insertAll(embeddings: List<EmbeddingEntity>): List<Long>

    /** Gallery rows for ONE model version only; mixing versions is never valid. */
    @Query("SELECT personId, vector, dim FROM embedding WHERE modelVersion = :modelVersion ORDER BY embeddingId")
    suspend fun galleryFor(modelVersion: String): List<GalleryRow>

    @Query("SELECT COUNT(*) FROM embedding")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM embedding WHERE personId = :personId")
    suspend fun countForPerson(personId: Long): Int

    @Query("SELECT DISTINCT modelVersion FROM embedding")
    suspend fun modelVersions(): List<String>
}

@Dao
interface AuditDao {
    @Insert suspend fun insert(entry: AuditEntity): Long

    @Query("SELECT * FROM audit ORDER BY auditId DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AuditEntity>

    @Query("SELECT COUNT(*) FROM audit")
    suspend fun count(): Int
}
