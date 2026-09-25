package com.patrick.faceid.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.patrick.faceid.face.QualityThresholds
import com.patrick.faceid.match.DecisionThresholds
import com.patrick.faceid.registration.RegistrationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("recognition_settings")

/**
 * Persists [RecognitionSettings] so thresholds can be changed at runtime, from a debug screen or
 * an experiment script, without rebuilding the app.
 *
 * Any value not yet stored falls back to the documented default, so a fresh install and an
 * upgrade both behave predictably.
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<RecognitionSettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): RecognitionSettings = settings.first()

    suspend fun update(settings: RecognitionSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[MATCH_THRESHOLD] = settings.decision.matchThreshold
            prefs[MARGIN_THRESHOLD] = settings.decision.marginThreshold
            prefs[MIN_QUALITY] = settings.decision.minQuality
            prefs[MIN_INTER_OCULAR_PX] = settings.quality.minInterOcularPx
            prefs[MIN_BLUR_VARIANCE] = settings.quality.minBlurVariance
            prefs[MIN_MEAN_LUMINANCE] = settings.quality.minMeanLuminance
            prefs[MAX_MEAN_LUMINANCE] = settings.quality.maxMeanLuminance
            prefs[MAX_CLIPPED_FRACTION] = settings.quality.maxClippedFraction
            prefs[MAX_ABS_YAW] = settings.quality.maxAbsYaw
            prefs[MAX_ABS_PITCH] = settings.quality.maxAbsPitch
            prefs[MAX_ABS_ROLL] = settings.quality.maxAbsRoll
            prefs[MIN_ACCEPTABLE_SCORE] = settings.quality.minAcceptableScore
            prefs[IMAGES_PER_PERSON] = settings.registration.imagesPerPerson
            prefs[DUPLICATE_WARN_THRESHOLD] = settings.registration.duplicateWarnThreshold
        }
    }

    /** Back to the documented defaults, for a clean experiment run. */
    suspend fun reset() {
        context.settingsDataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings(): RecognitionSettings {
        val defaults = RecognitionSettings()
        return RecognitionSettings(
            decision = DecisionThresholds(
                matchThreshold = this[MATCH_THRESHOLD] ?: defaults.decision.matchThreshold,
                marginThreshold = this[MARGIN_THRESHOLD] ?: defaults.decision.marginThreshold,
                minQuality = this[MIN_QUALITY] ?: defaults.decision.minQuality,
            ),
            quality = QualityThresholds(
                minInterOcularPx = this[MIN_INTER_OCULAR_PX] ?: defaults.quality.minInterOcularPx,
                minBlurVariance = this[MIN_BLUR_VARIANCE] ?: defaults.quality.minBlurVariance,
                minMeanLuminance = this[MIN_MEAN_LUMINANCE] ?: defaults.quality.minMeanLuminance,
                maxMeanLuminance = this[MAX_MEAN_LUMINANCE] ?: defaults.quality.maxMeanLuminance,
                maxClippedFraction = this[MAX_CLIPPED_FRACTION] ?: defaults.quality.maxClippedFraction,
                maxAbsYaw = this[MAX_ABS_YAW] ?: defaults.quality.maxAbsYaw,
                maxAbsPitch = this[MAX_ABS_PITCH] ?: defaults.quality.maxAbsPitch,
                maxAbsRoll = this[MAX_ABS_ROLL] ?: defaults.quality.maxAbsRoll,
                minAcceptableScore = this[MIN_ACCEPTABLE_SCORE] ?: defaults.quality.minAcceptableScore,
            ),
            registration = RegistrationConfig(
                imagesPerPerson = this[IMAGES_PER_PERSON] ?: defaults.registration.imagesPerPerson,
                duplicateWarnThreshold = this[DUPLICATE_WARN_THRESHOLD]
                    ?: defaults.registration.duplicateWarnThreshold,
            ),
        )
    }

    private companion object {
        val MATCH_THRESHOLD = floatPreferencesKey("match_threshold")
        val MARGIN_THRESHOLD = floatPreferencesKey("margin_threshold")
        val MIN_QUALITY = floatPreferencesKey("min_quality")
        val MIN_INTER_OCULAR_PX = floatPreferencesKey("min_inter_ocular_px")
        val MIN_BLUR_VARIANCE = floatPreferencesKey("min_blur_variance")
        val MIN_MEAN_LUMINANCE = floatPreferencesKey("min_mean_luminance")
        val MAX_MEAN_LUMINANCE = floatPreferencesKey("max_mean_luminance")
        val MAX_CLIPPED_FRACTION = floatPreferencesKey("max_clipped_fraction")
        val MAX_ABS_YAW = floatPreferencesKey("max_abs_yaw")
        val MAX_ABS_PITCH = floatPreferencesKey("max_abs_pitch")
        val MAX_ABS_ROLL = floatPreferencesKey("max_abs_roll")
        val MIN_ACCEPTABLE_SCORE = floatPreferencesKey("min_acceptable_score")
        val IMAGES_PER_PERSON = intPreferencesKey("images_per_person")
        val DUPLICATE_WARN_THRESHOLD = floatPreferencesKey("duplicate_warn_threshold")
    }
}
