package com.patrick.faceid.match

/**
 * One aggregated similarity score per registered person (never per embedding).
 * [score] is what the decision uses; the other fields are recorded for evaluation.
 */
data class PersonScore(
    val personId: Long,
    val score: Float,
    val maxScore: Float = score,
    val meanTop2: Float = score,
    val embeddingCount: Int = 1,
)

enum class Outcome { MATCH, UNCERTAIN, UNKNOWN }

enum class DecisionReason {
    /** All criteria satisfied. */
    ACCEPTED,
    /** Input quality below the configured floor; never evaluated for acceptance. */
    LOW_QUALITY,
    /** Nobody is registered. */
    EMPTY_GALLERY,
    /** Best candidate below the match threshold. */
    BELOW_MATCH_THRESHOLD,
    /** Best candidate clears the threshold but is too close to a different person. */
    AMBIGUOUS_MARGIN,
}

/**
 * Configurable, UNCALIBRATED criteria. No value here is a claim that a given similarity means
 * a correct identity; calibration is a Phase 2/3 experiment.
 */
data class DecisionThresholds(
    val matchThreshold: Float,
    val marginThreshold: Float,
    val minQuality: Float,
) {
    init {
        require(marginThreshold >= 0f) { "marginThreshold must be >= 0" }
        require(minQuality in 0f..1f) { "minQuality must be in [0, 1]" }
    }
}

data class Decision(
    val outcome: Outcome,
    val reason: DecisionReason,
    val best: PersonScore?,
    /** Best score belonging to a DIFFERENT person than [best]; null if nobody else exists. */
    val runnerUp: PersonScore?,
    /** best - runnerUp; null when there is no competing person. */
    val margin: Float?,
    val quality: Float,
    val thresholds: DecisionThresholds,
)

/**
 * Turns per-person similarity scores into MATCH / UNCERTAIN / UNKNOWN.
 *
 * Rules, in order:
 *  1. quality < minQuality                       -> UNCERTAIN (LOW_QUALITY)
 *  2. no registered people                       -> UNKNOWN   (EMPTY_GALLERY)
 *  3. best < matchThreshold                      -> UNKNOWN   (BELOW_MATCH_THRESHOLD)
 *  4. best - runnerUp < marginThreshold          -> UNCERTAIN (AMBIGUOUS_MARGIN)
 *  5. otherwise                                  -> MATCH
 *
 * The nearest candidate is therefore never accepted merely for being nearest.
 *
 * With a single registered person there is no competitor, so rule 4 cannot apply and only
 * the absolute threshold protects against a false match. This is a known weakness of very
 * small galleries and is recorded in the Phase 1 report.
 */
class RecognitionDecisionEngine(private val thresholds: DecisionThresholds) {

    fun decide(personScores: List<PersonScore>, quality: Float): Decision {
        require(personScores.map { it.personId }.toSet().size == personScores.size) {
            "personScores must contain one aggregated score per person"
        }
        require(personScores.none { it.score.isNaN() } && !quality.isNaN()) { "NaN score or quality" }

        val ranked = personScores.sortedByDescending { it.score }
        val best = ranked.getOrNull(0)
        val runnerUp = ranked.getOrNull(1)
        val margin = if (best != null && runnerUp != null) best.score - runnerUp.score else null

        fun decision(outcome: Outcome, reason: DecisionReason) =
            Decision(outcome, reason, best, runnerUp, margin, quality, thresholds)

        return when {
            quality < thresholds.minQuality -> decision(Outcome.UNCERTAIN, DecisionReason.LOW_QUALITY)
            best == null -> decision(Outcome.UNKNOWN, DecisionReason.EMPTY_GALLERY)
            best.score < thresholds.matchThreshold ->
                decision(Outcome.UNKNOWN, DecisionReason.BELOW_MATCH_THRESHOLD)
            margin != null && margin < thresholds.marginThreshold ->
                decision(Outcome.UNCERTAIN, DecisionReason.AMBIGUOUS_MARGIN)
            else -> decision(Outcome.MATCH, DecisionReason.ACCEPTED)
        }
    }
}
