package com.patrick.faceid.face

import kotlin.math.roundToInt

/**
 * 5-point face alignment to the standard ArcFace 112x112 template
 * (insightface v0.7 utils/face_align.py: estimate_norm + norm_crop).
 *
 * Fits a least-squares 2D similarity transform (rotation, uniform scale, translation; no shear,
 * no reflection) from the landmarks to the template, then samples bilinearly with a black
 * border, as cv2.warpAffine does.
 */
object FaceAligner {

    const val SIZE = 112

    /** ArcFace destination template: eye, eye, nose, mouth corner, mouth corner (image order). */
    val ARCFACE_TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f,
    )

    /**
     * Similarity transform mapping [src] points onto [dst] points (both interleaved x,y).
     * Returns [a, b, tx, ty] for:  x' = a*x - b*y + tx,  y' = b*x + a*y + ty.
     */
    fun estimateSimilarity(src: FloatArray, dst: FloatArray): FloatArray {
        require(src.size == dst.size && src.size % 2 == 0 && src.size >= 4)
        val n = src.size / 2
        var sx = 0.0; var sy = 0.0; var dx = 0.0; var dy = 0.0
        for (i in 0 until n) {
            sx += src[2 * i]; sy += src[2 * i + 1]; dx += dst[2 * i]; dy += dst[2 * i + 1]
        }
        sx /= n; sy /= n; dx /= n; dy /= n

        var dot = 0.0; var cross = 0.0; var norm = 0.0
        for (i in 0 until n) {
            val px = src[2 * i] - sx; val py = src[2 * i + 1] - sy
            val qx = dst[2 * i] - dx; val qy = dst[2 * i + 1] - dy
            dot += px * qx + py * qy
            cross += px * qy - py * qx
            norm += px * px + py * py
        }
        require(norm > 1e-9) { "degenerate landmarks" }
        val a = dot / norm
        val b = cross / norm
        val tx = dx - (a * sx - b * sy)
        val ty = dy - (b * sx + a * sy)
        return floatArrayOf(a.toFloat(), b.toFloat(), tx.toFloat(), ty.toFloat())
    }

    /** Warp [image] into a SIZE x SIZE crop aligned on [landmarks] (5 points, image order). */
    fun align(image: RgbImage, landmarks: FloatArray): RgbImage {
        require(landmarks.size == 10 && landmarks.none { it.isNaN() }) { "need 5 valid landmarks" }
        val (a, b, tx, ty) = estimateSimilarity(landmarks, ARCFACE_TEMPLATE).let {
            listOf(it[0], it[1], it[2], it[3])
        }
        // Inverse of [[a, -b], [b, a]] is [[a, b], [-b, a]] / (a^2 + b^2).
        val det = a * a + b * b
        val out = IntArray(SIZE * SIZE)
        for (v in 0 until SIZE) {
            for (u in 0 until SIZE) {
                val ux = u - tx
                val vy = v - ty
                val x = (a * ux + b * vy) / det
                val y = (-b * ux + a * vy) / det
                out[v * SIZE + u] = RgbImage.argb(
                    image.sample(x, y, 16).roundToInt().coerceIn(0, 255),
                    image.sample(x, y, 8).roundToInt().coerceIn(0, 255),
                    image.sample(x, y, 0).roundToInt().coerceIn(0, 255),
                )
            }
        }
        return RgbImage(SIZE, SIZE, out)
    }
}
