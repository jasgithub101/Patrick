package com.patrick.faceid.face

/**
 * Platform-neutral image: packed ARGB pixels, row-major. The recognition core uses only this
 * type (never android.graphics.Bitmap) so it runs unchanged in JVM unit tests.
 */
class RgbImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(width > 0 && height > 0) { "empty image" }
        require(argb.size == width * height) { "pixel count ${argb.size} != $width x $height" }
    }

    fun red(x: Int, y: Int): Int = (argb[y * width + x] shr 16) and 0xFF
    fun green(x: Int, y: Int): Int = (argb[y * width + x] shr 8) and 0xFF
    fun blue(x: Int, y: Int): Int = argb[y * width + x] and 0xFF

    /**
     * Bilinear sample of one channel at a sub-pixel position, using pixel-index coordinates
     * (pixel (x, y) is at exactly (x, y), as in OpenCV). Out-of-bounds neighbours read as 0.
     * [shift] selects the channel: 16 = red, 8 = green, 0 = blue.
     */
    fun sample(fx: Float, fy: Float, shift: Int): Float {
        val x0 = kotlin.math.floor(fx).toInt()
        val y0 = kotlin.math.floor(fy).toInt()
        val ax = fx - x0
        val ay = fy - y0
        fun px(x: Int, y: Int): Float =
            if (x < 0 || y < 0 || x >= width || y >= height) 0f
            else ((argb[y * width + x] shr shift) and 0xFF).toFloat()
        val top = px(x0, y0) * (1 - ax) + px(x0 + 1, y0) * ax
        val bottom = px(x0, y0 + 1) * (1 - ax) + px(x0 + 1, y0 + 1) * ax
        return top * (1 - ay) + bottom * ay
    }

    companion object {
        fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
