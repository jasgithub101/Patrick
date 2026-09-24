package com.patrick.faceid.match

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Immutable in-memory snapshot of all stored embeddings for ONE embedding-model version.
 * Rows are L2-normalised, so cosine similarity is a plain dot product.
 */
class EmbeddingGallery(
    val modelVersion: String,
    val dim: Int,
    private val personIds: LongArray,
    /** Row-major, `personIds.size * dim` floats. Row i belongs to personIds[i]. */
    private val vectors: FloatArray,
) {
    init {
        require(dim > 0)
        require(vectors.size == personIds.size * dim) { "vectors.size must equal rows * dim" }
        for (row in personIds.indices) {
            val n = VectorMath.norm(vectors, row * dim, dim)
            require(abs(n - 1f) < NORM_TOLERANCE) { "row $row is not L2-normalised (norm=$n)" }
        }
    }

    val size: Int get() = personIds.size

    fun personIdAt(row: Int): Long = personIds[row]

    fun dot(row: Int, query: FloatArray): Float {
        var s = 0f
        val off = row * dim
        for (j in 0 until dim) s += vectors[off + j] * query[j]
        return s
    }

    companion object {
        const val NORM_TOLERANCE = 1e-3f
        fun empty(modelVersion: String, dim: Int) = EmbeddingGallery(modelVersion, dim, LongArray(0), FloatArray(0))
    }
}

/** How a person's several embedding similarities become one person score. A Phase 2 experiment. */
enum class Aggregation { MAX, MEAN_TOP2 }

interface FaceMatcher {
    /** Returns exactly one [PersonScore] per registered person, in no particular order. */
    fun scorePersons(query: FloatArray, queryModelVersion: String, gallery: EmbeddingGallery): List<PersonScore>
}

/**
 * Exact search over every stored embedding. Adequate for the Phase 1 gallery (~100 people,
 * ~500 embeddings). ANN search is deferred until measurements show this is too slow.
 */
class BruteForceCosineMatcher(private val aggregation: Aggregation = Aggregation.MAX) : FaceMatcher {

    override fun scorePersons(
        query: FloatArray,
        queryModelVersion: String,
        gallery: EmbeddingGallery,
    ): List<PersonScore> {
        require(queryModelVersion == gallery.modelVersion) {
            "Refusing to compare embeddings across model versions " +
                "(query=$queryModelVersion, gallery=${gallery.modelVersion})"
        }
        require(query.size == gallery.dim) { "query dim ${query.size} != gallery dim ${gallery.dim}" }
        val qn = VectorMath.norm(query, 0, query.size)
        require(abs(qn - 1f) < EmbeddingGallery.NORM_TOLERANCE) { "query is not L2-normalised (norm=$qn)" }

        // Per person: best and second-best similarity, and embedding count.
        val top1 = HashMap<Long, Float>()
        val top2 = HashMap<Long, Float>()
        val counts = HashMap<Long, Int>()
        for (row in 0 until gallery.size) {
            val id = gallery.personIdAt(row)
            val s = gallery.dot(row, query)
            counts[id] = (counts[id] ?: 0) + 1
            val b1 = top1[id]
            if (b1 == null || s > b1) {
                if (b1 != null) top2[id] = b1
                top1[id] = s
            } else {
                val b2 = top2[id]
                if (b2 == null || s > b2) top2[id] = s
            }
        }

        return top1.map { (id, max) ->
            val meanTop2 = top2[id]?.let { (max + it) / 2f } ?: max
            val score = when (aggregation) {
                Aggregation.MAX -> max
                Aggregation.MEAN_TOP2 -> meanTop2
            }
            PersonScore(id, score, max, meanTop2, counts.getValue(id))
        }
    }
}

object VectorMath {
    fun norm(v: FloatArray, offset: Int, length: Int): Float {
        var s = 0.0
        for (i in offset until offset + length) s += v[i].toDouble() * v[i]
        return sqrt(s).toFloat()
    }

    fun l2Normalize(v: FloatArray): FloatArray {
        val n = norm(v, 0, v.size)
        require(n > 0f) { "cannot normalise a zero vector" }
        return FloatArray(v.size) { v[it] / n }
    }
}
