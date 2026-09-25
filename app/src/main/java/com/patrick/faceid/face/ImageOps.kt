package com.patrick.faceid.face

/**
 * Small image-analysis helpers used by the quality checker. Pure maths on plain arrays, so it
 * runs in JVM unit tests with synthetic images.
 */
object ImageOps {

    /** Rec. 601 luma, the usual weighting for perceived brightness. */
    fun luminance(r: Float, g: Float, b: Float): Float = 0.299f * r + 0.587f * g + 0.114f * b

    /**
     * Crop [box] out of [image] and resample it to [size] x [size] greyscale.
     *
     * Resampling to a fixed size is what makes blur and brightness thresholds comparable between
     * a face filling the frame and a face far away: otherwise the same face at two distances
     * would score differently purely because of pixel count.
     *
     * Values are 0..255. Samples outside the image read as 0 (the same black border the aligner
     * uses).
     */
    fun cropToGrey(image: RgbImage, box: Box, size: Int): FloatArray {
        require(size > 1) { "size must be > 1" }
        require(box.width > 0f && box.height > 0f) { "empty crop box" }
        val out = FloatArray(size * size)
        val stepX = box.width / size
        val stepY = box.height / size
        for (j in 0 until size) {
            val y = box.y1 + (j + 0.5f) * stepY
            for (i in 0 until size) {
                val x = box.x1 + (i + 0.5f) * stepX
                out[j * size + i] = luminance(
                    image.sample(x, y, 16),
                    image.sample(x, y, 8),
                    image.sample(x, y, 0),
                )
            }
        }
        return out
    }

    /**
     * Variance of the Laplacian: the standard cheap focus measure. A sharp image has strong
     * second derivatives and therefore high variance; a blurred one has little. The border ring is
     * skipped because the 3x3 kernel is undefined there.
     */
    fun laplacianVariance(grey: FloatArray, size: Int): Float {
        require(grey.size == size * size) { "grey array does not match size" }
        require(size >= 3) { "need at least 3x3" }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until size - 1) {
            for (x in 1 until size - 1) {
                val i = y * size + x
                val value = (grey[i - 1] + grey[i + 1] + grey[i - size] + grey[i + size] - 4f * grey[i])
                sum += value
                sumSq += value.toDouble() * value
                n++
            }
        }
        if (n == 0) return 0f
        val mean = sum / n
        return ((sumSq / n) - mean * mean).toFloat()
    }

    /** Mean brightness (0..255) and the fraction of pixels crushed to black or blown out to white. */
    fun brightness(grey: FloatArray): Pair<Float, Float> {
        if (grey.isEmpty()) return 0f to 0f
        var sum = 0.0
        var clipped = 0
        for (v in grey) {
            sum += v
            if (v <= CLIP_LOW || v >= CLIP_HIGH) clipped++
        }
        return (sum / grey.size).toFloat() to (clipped.toFloat() / grey.size)
    }

    private const val CLIP_LOW = 5f
    private const val CLIP_HIGH = 250f
}
