package com.patrick.faceid.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionDecisionEngineTest {

    // Values chosen to be exactly representable in binary floating point so boundary
    // tests are not at the mercy of rounding. They are test fixtures, not calibrated values.
    private val thresholds = DecisionThresholds(matchThreshold = 0.5f, marginThreshold = 0.125f, minQuality = 0.5f)
    private val engine = RecognitionDecisionEngine(thresholds)

    private fun scores(vararg pairs: Pair<Long, Float>) = pairs.map { PersonScore(it.first, it.second) }

    @Test fun emptyGalleryIsUnknown() {
        val d = engine.decide(emptyList(), quality = 1f)
        assertEquals(Outcome.UNKNOWN, d.outcome)
        assertEquals(DecisionReason.EMPTY_GALLERY, d.reason)
    }

    @Test fun clearWinnerIsMatch() {
        val d = engine.decide(scores(1L to 0.84f, 2L to 0.51f), quality = 0.9f)
        assertEquals(Outcome.MATCH, d.outcome)
        assertEquals(1L, d.best?.personId)
        assertEquals(2L, d.runnerUp?.personId)
        assertEquals(0.33f, d.margin!!, 1e-6f)
    }

    @Test fun nearestCandidateIsNotForcedWhenAllAreWeak() {
        // Person 3 is the nearest, but nobody clears the threshold.
        val d = engine.decide(scores(1L to 0.20f, 2L to 0.31f, 3L to 0.44f), quality = 1f)
        assertEquals(Outcome.UNKNOWN, d.outcome)
        assertEquals(DecisionReason.BELOW_MATCH_THRESHOLD, d.reason)
        assertEquals(3L, d.best?.personId)
    }

    @Test fun tinyMarginIsUncertainEvenAboveThreshold() {
        // The 0.83 vs 0.82 case from the design discussion.
        val d = engine.decide(scores(1L to 0.83f, 2L to 0.82f), quality = 1f)
        assertEquals(Outcome.UNCERTAIN, d.outcome)
        assertEquals(DecisionReason.AMBIGUOUS_MARGIN, d.reason)
    }

    @Test fun lowQualityIsUncertainEvenWithAPerfectScore() {
        val d = engine.decide(scores(1L to 0.99f, 2L to 0.10f), quality = 0.49f)
        assertEquals(Outcome.UNCERTAIN, d.outcome)
        assertEquals(DecisionReason.LOW_QUALITY, d.reason)
    }

    @Test fun lowQualityTakesPrecedenceOverEmptyGallery() {
        val d = engine.decide(emptyList(), quality = 0f)
        assertEquals(DecisionReason.LOW_QUALITY, d.reason)
    }

    @Test fun singleRegisteredPersonHasNoMargin() {
        val d = engine.decide(scores(7L to 0.75f), quality = 1f)
        assertEquals(Outcome.MATCH, d.outcome)
        assertNull(d.runnerUp)
        assertNull(d.margin)
    }

    @Test fun boundariesAreInclusive() {
        // score == matchThreshold and margin == marginThreshold both pass.
        val d = engine.decide(scores(1L to 0.625f, 2L to 0.5f), quality = 0.5f)
        assertEquals(Outcome.MATCH, d.outcome)
    }

    @Test fun justBelowMarginIsUncertain() {
        val d = engine.decide(scores(1L to 0.625f, 2L to 0.5078125f), quality = 1f) // margin 0.1171875
        assertEquals(Outcome.UNCERTAIN, d.outcome)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPerEmbeddingInputWithRepeatedPerson() {
        // Guards the bug-trap: feeding per-embedding scores would make the runner-up the same person.
        engine.decide(scores(1L to 0.90f, 1L to 0.89f, 2L to 0.40f), quality = 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNaN() {
        engine.decide(scores(1L to Float.NaN), quality = 1f)
    }

    @Test fun decisionIsIndependentOfInputOrder() {
        val a = engine.decide(scores(1L to 0.4f, 2L to 0.9f, 3L to 0.6f), quality = 1f)
        val b = engine.decide(scores(3L to 0.6f, 1L to 0.4f, 2L to 0.9f), quality = 1f)
        assertEquals(a.outcome, b.outcome)
        assertEquals(a.best, b.best)
        assertEquals(a.runnerUp, b.runnerUp)
    }
}
