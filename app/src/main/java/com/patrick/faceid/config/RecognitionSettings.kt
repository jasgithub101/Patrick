package com.patrick.faceid.config

import com.patrick.faceid.face.QualityThresholds
import com.patrick.faceid.match.DecisionThresholds
import com.patrick.faceid.registration.RegistrationConfig

/**
 * Every tunable parameter in one place.
 *
 * NONE of these values is validated. They are starting points for experiments, not calibrated
 * operating points, and Phase 1 evidence comes from 10 identities (report E2). They are held
 * here, and persisted, precisely so Phase 2 can sweep them without changing code.
 */
data class RecognitionSettings(
    val decision: DecisionThresholds = DEFAULT_DECISION,
    val quality: QualityThresholds = QualityThresholds(),
    val registration: RegistrationConfig = RegistrationConfig(),
) {
    companion object {
        /**
         * matchThreshold is 0.35, not the 0.5 used in E2: that run showed 0.5 would reject a
         * genuine probe scoring 0.441 while every never-enrolled person stayed below 0.242.
         * Still uncalibrated, but 0.5 is demonstrably too high for this model.
         *
         * minQuality is 0.10 because the hard quality gates already reject bad input, so this is
         * only a backstop. E3 measured genuine LFW faces scoring about 0.2 at the 5th percentile.
         */
        val DEFAULT_DECISION = DecisionThresholds(
            matchThreshold = 0.35f,
            marginThreshold = 0.10f,
            minQuality = 0.10f,
        )
    }
}
