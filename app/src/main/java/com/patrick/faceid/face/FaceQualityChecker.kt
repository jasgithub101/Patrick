package com.patrick.faceid.face

import kotlin.math.abs
import kotlin.math.min

/** Why an input was rejected. Each maps to an actionable operator message. */
enum class QualityIssue {
    NO_FACE,
    MULTIPLE_FACES,
    LANDMARKS_MISSING,
    FACE_TOO_SMALL,
    TOO_BLURRY,
    TOO_DARK,
    TOO_BRIGHT,
    EXTREME_POSE,
}

/** What was actually measured, so a rejection can be explained and logged. */
data class QualityMetrics(
    val facesDetected: Int,
    val interOcularPx: Float,
    val blurVariance: Float,
    val meanLuminance: Float,
    val clippedFraction: Float,
    val yaw: Float?,
    val pitch: Float?,
    val roll: Float?,
)

/**
 * Thresholds for accepting an image. UNCALIBRATED starting values, chosen to be permissive
 * enough not to reject ordinary photos, then measured against LFW in M4. Runtime-configurable,
 * never hard-coded at a call site.
 */
data class QualityThresholds(
    /**
     * Eye-to-eye distance floor. CALIBRATED from measured LFW faces (report E3: min 21, p5 32,
     * median 40 px) and set below the observed minimum so ordinary photos are not rejected.
     * Note the ArcFace template itself spans about 35 px between the eyes at 112x112, so faces
     * near this floor are being upsampled. A camera-based deployment should raise it.
     */
    val minInterOcularPx: Float = 18f,
    /**
     * Variance of the Laplacian on a fixed-size face crop. CALIBRATED from E3: genuine faces
     * measured 16 to 750 (median 105). The first guess of 60 rejected 16 percent of ordinary
     * photos, so the floor sits below the observed minimum.
     */
    val minBlurVariance: Float = 12f,
    val minMeanLuminance: Float = 40f,
    val maxMeanLuminance: Float = 235f,
    val maxClippedFraction: Float = 0.35f,
    /**
     * How large a second face may be, relative to the largest, before the frame counts as
     * ambiguous. A distant bystander is ignored; two people both presenting to the camera are
     * rejected, because the system must not guess which one the operator meant.
     */
    val maxSecondFaceAreaRatio: Float = 0.5f,
    /** Head rotation limits in degrees; applied only when the detector reports angles. */
    val maxAbsYaw: Float = 45f,
    val maxAbsPitch: Float = 40f,
    val maxAbsRoll: Float = 30f,
    /**
     * Score below which input counts as unusable. Deliberately low: the hard gates above are the
     * real protection, and this only catches near-garbage. E3 measured a median score of about
     * 0.5 and a 5th percentile near 0.2 on genuine LFW faces, whose low resolution holds the size
     * factor down.
     */
    val minAcceptableScore: Float = 0.10f,
) {
    init {
        require(minInterOcularPx > 0f)
        require(minMeanLuminance < maxMeanLuminance)
        require(maxClippedFraction in 0f..1f)
        require(maxSecondFaceAreaRatio in 0f..1f)
        require(minAcceptableScore in 0f..1f)
    }
}

/** Either a usable face with a quality score, or a rejection with a reason and a message. */
sealed interface QualityAssessment {

    val metrics: QualityMetrics?

    data class Rejected(
        val issue: QualityIssue,
        /** Actionable wording for the operator, never a bare "recognition failed". */
        val message: String,
        override val metrics: QualityMetrics?,
    ) : QualityAssessment

    data class Accepted(
        val face: DetectedFace,
        /** 0..1, the weakest individual factor. Fed to the decision engine. */
        val score: Float,
        override val metrics: QualityMetrics,
    ) : QualityAssessment
}

/**
 * Decides whether an image is good enough to embed.
 *
 * Poor input is REJECTED so the caller can ask for another capture, rather than storing or
 * matching a low-quality embedding. This is the gate that keeps bad data out of the gallery.
 *
 * The overall score is the minimum of the individual factor scores: quality is limited by its
 * weakest aspect, so a well-lit but badly blurred face is not rescued by its lighting.
 */
class FaceQualityChecker(private val thresholds: QualityThresholds = QualityThresholds()) {

    fun assess(image: RgbImage, faces: List<DetectedFace>): QualityAssessment {
        if (faces.isEmpty()) {
            return reject(QualityIssue.NO_FACE, "No face detected. Point the camera at the person.", null)
        }
        // The subject is the most prominent face. A second face only makes the frame ambiguous
        // when it is comparably large; a bystander in the background does not.
        val sorted = faces.sortedByDescending { it.box.area }
        val face = sorted.first()
        val runnerUpArea = sorted.getOrNull(1)?.box.let { it?.area ?: 0f }
        if (face.box.area > 0f && runnerUpArea / face.box.area > thresholds.maxSecondFaceAreaRatio) {
            return reject(
                QualityIssue.MULTIPLE_FACES,
                "More than one person in view (" + faces.size + "). Only one person at a time.",
                null,
            )
        }
        if (!face.hasLandmarks) {
            return reject(
                QualityIssue.LANDMARKS_MISSING,
                "Face partially hidden. Remove anything covering the eyes, nose or mouth.",
                null,
            )
        }

        val grey = ImageOps.cropToGrey(image, face.box, CROP_SIZE)
        val blur = ImageOps.laplacianVariance(grey, CROP_SIZE)
        val brightness = ImageOps.brightness(grey)
        val metrics = QualityMetrics(
            facesDetected = faces.size,
            interOcularPx = face.interOcularDistance,
            blurVariance = blur,
            meanLuminance = brightness.first,
            clippedFraction = brightness.second,
            yaw = face.yaw,
            pitch = face.pitch,
            roll = face.roll,
        )

        if (metrics.interOcularPx < thresholds.minInterOcularPx) {
            return reject(QualityIssue.FACE_TOO_SMALL, "Face too far away. Move closer.", metrics)
        }
        if (metrics.meanLuminance < thresholds.minMeanLuminance) {
            return reject(QualityIssue.TOO_DARK, "Too dark. Move to a brighter place.", metrics)
        }
        if (metrics.meanLuminance > thresholds.maxMeanLuminance ||
            metrics.clippedFraction > thresholds.maxClippedFraction
        ) {
            return reject(
                QualityIssue.TOO_BRIGHT,
                "Too bright or strong glare. Move out of direct light.",
                metrics,
            )
        }
        // Lighting is gated BEFORE blur on purpose: a crushed or blown-out image has lost its
        // detail, so the blur test would also fail and give the operator the wrong instruction
        // ("hold steady") for what is really a lighting problem.
        if (blur < thresholds.minBlurVariance) {
            return reject(QualityIssue.TOO_BLURRY, "Image is blurred. Hold steady and try again.", metrics)
        }
        poseRejection(metrics)?.let { return it }

        return QualityAssessment.Accepted(face, score(metrics), metrics)
    }

    private fun poseRejection(metrics: QualityMetrics): QualityAssessment.Rejected? {
        val yaw = metrics.yaw ?: return null
        val pitch = metrics.pitch ?: 0f
        val roll = metrics.roll ?: 0f
        return when {
            abs(yaw) > thresholds.maxAbsYaw ->
                reject(QualityIssue.EXTREME_POSE, "Head turned too far. Look towards the camera.", metrics)
            abs(pitch) > thresholds.maxAbsPitch ->
                reject(QualityIssue.EXTREME_POSE, "Head tilted too far up or down. Look straight ahead.", metrics)
            abs(roll) > thresholds.maxAbsRoll ->
                reject(QualityIssue.EXTREME_POSE, "Head leaning too far to one side. Straighten up.", metrics)
            else -> null
        }
    }

    /** Each factor scaled to 0..1, then the weakest one wins. */
    private fun score(metrics: QualityMetrics): Float {
        val sizeScore = ratio(metrics.interOcularPx, thresholds.minInterOcularPx, GOOD_INTER_OCULAR_PX)
        val blurScore = ratio(metrics.blurVariance, thresholds.minBlurVariance, GOOD_BLUR_VARIANCE)
        val halfRange = (thresholds.maxMeanLuminance - thresholds.minMeanLuminance) / 2f
        val midLuminance = (thresholds.minMeanLuminance + thresholds.maxMeanLuminance) / 2f
        val luminanceScore = 1f - (abs(metrics.meanLuminance - midLuminance) / halfRange).coerceIn(0f, 1f)
        val clipScore = 1f - (metrics.clippedFraction / thresholds.maxClippedFraction).coerceIn(0f, 1f)
        val poseScore = metrics.yaw?.let { yaw ->
            1f - maxOf(
                abs(yaw) / thresholds.maxAbsYaw,
                abs(metrics.pitch ?: 0f) / thresholds.maxAbsPitch,
                abs(metrics.roll ?: 0f) / thresholds.maxAbsRoll,
            ).coerceIn(0f, 1f)
        } ?: 1f
        return min(min(min(sizeScore, blurScore), min(luminanceScore, clipScore)), poseScore)
            .coerceIn(0f, 1f)
    }

    /** 0 at [floor], 1 at [good], linear in between. */
    private fun ratio(value: Float, floor: Float, good: Float): Float =
        ((value - floor) / (good - floor)).coerceIn(0f, 1f)

    private fun reject(issue: QualityIssue, message: String, metrics: QualityMetrics?) =
        QualityAssessment.Rejected(issue, message, metrics)

    private companion object {
        /** Face crops are analysed at a fixed size so thresholds do not depend on distance. */
        const val CROP_SIZE = 112

        /**
         * Values at which a factor counts as fully good, used only for scoring. Set near the upper
         * end of what E3 measured on genuine faces, so scores span a useful range instead of
         * everything looking poor.
         */
        const val GOOD_INTER_OCULAR_PX = 60f
        const val GOOD_BLUR_VARIANCE = 150f
    }
}
