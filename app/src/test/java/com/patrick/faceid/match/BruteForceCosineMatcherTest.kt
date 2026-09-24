package com.patrick.faceid.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class BruteForceCosineMatcherTest {

    private val model = "w600k_mbf@test"
    private val query = floatArrayOf(1f, 0f, 0f, 0f)

    /** Unit vector whose cosine similarity to [query] is exactly [c] (up to float rounding). */
    private fun withCos(c: Float, axis: Int = 1): FloatArray =
        FloatArray(4).also { it[0] = c; it[axis] = sqrt(1f - c * c) }

    private fun gallery(vararg rows: Pair<Long, FloatArray>, version: String = model) =
        EmbeddingGallery(
            modelVersion = version,
            dim = 4,
            personIds = rows.map { it.first }.toLongArray(),
            vectors = rows.flatMap { it.second.toList() }.toFloatArray(),
        )

    @Test fun returnsOneScorePerPersonUsingMax() {
        val g = gallery(1L to withCos(0.6f), 1L to withCos(0.9f), 1L to withCos(0.7f), 2L to withCos(0.5f))
        val scores = BruteForceCosineMatcher(Aggregation.MAX).scorePersons(query, model, g).associateBy { it.personId }
        assertEquals(2, scores.size)
        assertEquals(0.9f, scores.getValue(1L).score, 1e-5f)
        assertEquals(3, scores.getValue(1L).embeddingCount)
        assertEquals(0.8f, scores.getValue(1L).meanTop2, 1e-5f)
        assertEquals(0.5f, scores.getValue(2L).score, 1e-5f)
    }

    @Test fun meanTop2Aggregation() {
        val g = gallery(1L to withCos(0.6f), 1L to withCos(0.9f), 1L to withCos(0.7f))
        val s = BruteForceCosineMatcher(Aggregation.MEAN_TOP2).scorePersons(query, model, g).single()
        assertEquals(0.8f, s.score, 1e-5f)
        assertEquals(0.9f, s.maxScore, 1e-5f)
    }

    @Test fun singleEmbeddingPersonMeanTop2EqualsMax() {
        val s = BruteForceCosineMatcher(Aggregation.MEAN_TOP2)
            .scorePersons(query, model, gallery(5L to withCos(0.7f))).single()
        assertEquals(0.7f, s.score, 1e-5f)
    }

    /**
     * The central bug-trap. Person 1 has two near-identical photos; person 2 is a weak
     * impostor. Ranking raw embeddings would put person 1 in both top slots (margin ~0.01) and
     * reject a clear match as ambiguous. Ranking one score per person gives the real margin.
     */
    @Test fun runnerUpIsADifferentPersonNotAnotherPhotoOfTheSamePerson() {
        val g = gallery(1L to withCos(0.90f), 1L to withCos(0.89f, axis = 2), 2L to withCos(0.40f))
        val scores = BruteForceCosineMatcher().scorePersons(query, model, g)
        val decision = RecognitionDecisionEngine(DecisionThresholds(0.5f, 0.125f, 0.5f)).decide(scores, 1f)

        assertEquals(Outcome.MATCH, decision.outcome)
        assertEquals(2L, decision.runnerUp?.personId)
        assertEquals(0.50f, decision.margin!!, 1e-4f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToCompareAcrossModelVersions() {
        BruteForceCosineMatcher().scorePersons(query, "some_other_model@v2", gallery(1L to withCos(0.9f)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDimensionMismatch() {
        BruteForceCosineMatcher().scorePersons(floatArrayOf(1f, 0f), model, gallery(1L to withCos(0.9f)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnnormalisedQuery() {
        BruteForceCosineMatcher().scorePersons(floatArrayOf(2f, 0f, 0f, 0f), model, gallery(1L to withCos(0.9f)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnnormalisedGalleryRow() {
        gallery(1L to floatArrayOf(3f, 0f, 0f, 0f))
    }

    @Test fun emptyGalleryYieldsNoScores() {
        assertTrue(BruteForceCosineMatcher().scorePersons(query, model, EmbeddingGallery.empty(model, 4)).isEmpty())
    }

    @Test fun l2NormalizeProducesUnitVector() {
        val v = VectorMath.l2Normalize(floatArrayOf(3f, 4f))
        assertEquals(1f, VectorMath.norm(v, 0, 2), 1e-6f)
        assertEquals(0.6f, v[0], 1e-6f)
    }
}
