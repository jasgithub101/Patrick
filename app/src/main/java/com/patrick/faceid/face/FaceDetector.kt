package com.patrick.faceid.face

/** Axis-aligned box in source-image pixel coordinates. */
data class Box(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    val width: Float get() = x2 - x1
    val height: Float get() = y2 - y1
    val area: Float get() = maxOf(0f, width) * maxOf(0f, height)
}

/**
 * A detected face. [landmarks] holds 5 (x, y) points in source-image pixels, in the InsightFace
 * order: left eye, right eye, nose tip, left mouth corner, right mouth corner, where "left" and
 * "right" are as seen in the image.
 */
class DetectedFace(
    val box: Box,
    val landmarks: FloatArray,
    /** Detector confidence, or NaN when the detector does not expose one (ML Kit). */
    val score: Float,
    /** Head rotation in degrees, when the detector provides it (ML Kit does). */
    val yaw: Float? = null,
    val pitch: Float? = null,
    val roll: Float? = null,
) {
    init { require(landmarks.size == 10) { "expected 5 landmark points" } }

    fun point(i: Int): Pair<Float, Float> = landmarks[2 * i] to landmarks[2 * i + 1]

    /** False when the detector could not locate all 5 points (they are then NaN). */
    val hasLandmarks: Boolean get() = landmarks.none { it.isNaN() }

    /** Distance between the eye centres, in pixels; the main face-size measure. */
    val interOcularDistance: Float
        get() {
            val dx = landmarks[2] - landmarks[0]
            val dy = landmarks[3] - landmarks[1]
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
}

interface FaceDetector : AutoCloseable {
    /** All detected faces, most prominent (largest) first. */
    fun detect(image: RgbImage): List<DetectedFace>
}
