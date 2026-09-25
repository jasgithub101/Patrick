package com.patrick.faceid.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageOpsTest {

    @Test fun luminanceWeightsGreenMostAsRec601Requires() {
        assertEquals(0f, ImageOps.luminance(0f, 0f, 0f), 1e-4f)
        assertEquals(255f, ImageOps.luminance(255f, 255f, 255f), 1e-3f)
        assertTrue(ImageOps.luminance(0f, 255f, 0f) > ImageOps.luminance(255f, 0f, 0f))
        assertTrue(ImageOps.luminance(255f, 0f, 0f) > ImageOps.luminance(0f, 0f, 255f))
    }

    @Test fun aFlatImageHasZeroLaplacianVariance() {
        val grey = FloatArray(16 * 16) { 100f }
        assertEquals(0f, ImageOps.laplacianVariance(grey, 16), 1e-4f)
    }

    @Test fun sharpDetailGivesHigherVarianceThanSmoothDetail() {
        val size = 32
        val checkerboard = FloatArray(size * size) { if (((it % size) + it / size) % 2 == 0) 0f else 255f }
        val gradient = FloatArray(size * size) { (it % size) * 255f / size }
        assertTrue(
            "a checkerboard must read as sharper than a linear ramp",
            ImageOps.laplacianVariance(checkerboard, size) > ImageOps.laplacianVariance(gradient, size),
        )
    }

    @Test fun aLinearRampHasAlmostNoSecondDerivative() {
        val size = 32
        val ramp = FloatArray(size * size) { (it % size) * 4f }
        assertEquals(0f, ImageOps.laplacianVariance(ramp, size), 1e-2f)
    }

    @Test fun brightnessReportsMeanAndClipping() {
        val (mean, clipped) = ImageOps.brightness(FloatArray(100) { 128f })
        assertEquals(128f, mean, 1e-3f)
        assertEquals(0f, clipped, 1e-6f)

        val halfBlown = FloatArray(100) { if (it < 50) 255f else 128f }
        val (_, clippedHalf) = ImageOps.brightness(halfBlown)
        assertEquals(0.5f, clippedHalf, 1e-6f)
    }

    @Test fun crushedBlacksCountAsClipped() {
        val (_, clipped) = ImageOps.brightness(FloatArray(10) { 0f })
        assertEquals(1f, clipped, 1e-6f)
    }

    @Test fun cropToGreyResamplesToTheRequestedSize() {
        val image = TestImages.noisy(200, 200)
        val grey = ImageOps.cropToGrey(image, Box(10f, 10f, 110f, 110f), 32)
        assertEquals(32 * 32, grey.size)
        assertTrue("values should be in 0..255", grey.all { it in 0f..255f })
    }

    @Test fun cropToGreyIsScaleInvariantForTheSameContent() {
        // The same flat patch at two crop sizes must give the same mean, so thresholds do not
        // depend on how far away the face is.
        val image = TestImages.flat(300, 300, 90)
        val small = ImageOps.brightness(ImageOps.cropToGrey(image, Box(0f, 0f, 50f, 50f), 112)).first
        val large = ImageOps.brightness(ImageOps.cropToGrey(image, Box(0f, 0f, 250f, 250f), 112)).first
        assertEquals(small, large, 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEmptyCropBoxIsRejected() {
        ImageOps.cropToGrey(TestImages.flat(10, 10, 5), Box(5f, 5f, 5f, 5f), 8)
    }
}
