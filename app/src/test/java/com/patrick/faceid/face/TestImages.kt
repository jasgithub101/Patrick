package com.patrick.faceid.face

import kotlin.math.sin
import kotlin.random.Random

/** Synthetic images and faces for JVM quality tests. */
object TestImages {

    /** Flat grey: no texture at all, so the Laplacian variance is zero. */
    fun flat(width: Int, height: Int, level: Int): RgbImage =
        RgbImage(width, height, IntArray(width * height) { RgbImage.argb(level, level, level) })

    /** High-frequency noise: sharp, so blur variance is large. */
    fun noisy(width: Int, height: Int, seed: Int = 1, mean: Int = 128, amplitude: Int = 90): RgbImage {
        val random = Random(seed)
        return RgbImage(width, height, IntArray(width * height) {
            val v = (mean + random.nextInt(-amplitude, amplitude + 1)).coerceIn(0, 255)
            RgbImage.argb(v, v, v)
        })
    }

    /** A smooth gradient: some structure, but low second derivative, so it reads as blurred. */
    fun smooth(width: Int, height: Int, mean: Int = 128): RgbImage =
        RgbImage(width, height, IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val v = (mean + 40 * sin(x / 40f) * sin(y / 40f)).toInt().coerceIn(0, 255)
            RgbImage.argb(v, v, v)
        })

    /**
     * A face whose landmarks give the requested inter-ocular distance, centred in [box].
     * Landmark order matches the detector contract: eye, eye, nose, mouth, mouth.
     */
    fun face(
        box: Box = Box(20f, 20f, 120f, 140f),
        interOcularPx: Float = 40f,
        yaw: Float? = 0f,
        pitch: Float? = 0f,
        roll: Float? = 0f,
        landmarksValid: Boolean = true,
    ): DetectedFace {
        val cx = (box.x1 + box.x2) / 2f
        val cy = (box.y1 + box.y2) / 2f
        val half = interOcularPx / 2f
        val landmarks = if (landmarksValid) {
            floatArrayOf(
                cx - half, cy - 10f,
                cx + half, cy - 10f,
                cx, cy + 5f,
                cx - half * 0.8f, cy + 25f,
                cx + half * 0.8f, cy + 25f,
            )
        } else {
            FloatArray(10) { Float.NaN }
        }
        return DetectedFace(box, landmarks, Float.NaN, yaw, pitch, roll)
    }
}
