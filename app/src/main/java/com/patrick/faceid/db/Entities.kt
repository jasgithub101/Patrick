package com.patrick.faceid.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A registered person.
 *
 * [dummyAbhaId] is a PLACEHOLDER, never a real ABHA number. It carries a "DUMMY-" prefix so it
 * can never be mistaken for or used as a real health identifier. Real ABHA/ABDM linkage is
 * explicitly out of scope for Phase 1 and must go through the authorised ABDM workflow.
 */
@Entity(tableName = "person", indices = [Index(value = ["dummyAbhaId"], unique = true)])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val personId: Long = 0,
    val dummyAbhaId: String,
    val displayName: String,
    val createdAt: Long,
)

/**
 * One face embedding belonging to a person. A person has several (5 by default).
 *
 * [vector] is the raw little-endian float32 embedding; [EmbeddingCodec] converts it.
 * [modelVersion] and [dim] are stored per row because embeddings from different models are not
 * comparable, and the matcher refuses to mix them.
 *
 * Note: this is a data class holding a ByteArray, so generated equals/hashCode compare the array
 * by reference. Nothing relies on entity equality.
 */
@Entity(
    tableName = "embedding",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["personId"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("personId"), Index("modelVersion")],
)
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val embeddingId: Long = 0,
    val personId: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val vector: ByteArray,
    val modelVersion: String,
    val dim: Int,
    /** Which guided capture this came from, e.g. "frontal", "left", or "bulk" for dataset imports. */
    val captureRole: String,
    val createdAt: Long,
)

/** Append-only record of significant operations. Scores and outcomes only, never image data. */
@Entity(tableName = "audit", indices = [Index("timestamp")])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val auditId: Long = 0,
    val timestamp: Long,
    val operation: String,
    val outcome: String,
    val personId: Long?,
    val details: String,
)
