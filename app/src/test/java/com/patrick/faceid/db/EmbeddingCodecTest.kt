package com.patrick.faceid.db

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingCodecTest {

    @Test fun roundTripsExactly() {
        val original = FloatArray(512) { (it - 256) / 137f }
        val decoded = EmbeddingCodec.decode(EmbeddingCodec.encode(original))
        // float32 -> bytes -> float32 is lossless, so require exact equality.
        assertArrayEquals(original, decoded, 0f)
    }

    @Test fun encodesFourBytesPerValue() {
        assertEquals(512 * 4, EmbeddingCodec.encode(FloatArray(512)).size)
    }

    @Test fun preservesNegativeAndTinyValues() {
        val original = floatArrayOf(-1f, -0.5f, 0f, Float.MIN_VALUE, 1e-8f, 0.999999f)
        assertArrayEquals(original, EmbeddingCodec.decode(EmbeddingCodec.encode(original)), 0f)
    }

    @Test fun byteOrderIsLittleEndianSoFilesArePortable() {
        // 1.0f is 0x3F800000; little-endian puts the low byte first.
        assertArrayEquals(
            byteArrayOf(0, 0, 0x80.toByte(), 0x3F),
            EmbeddingCodec.encode(floatArrayOf(1f)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlobThatIsNotAWholeNumberOfFloats() {
        EmbeddingCodec.decode(ByteArray(7))
    }

    @Test fun handlesEmptyVector() {
        assertEquals(0, EmbeddingCodec.decode(EmbeddingCodec.encode(FloatArray(0))).size)
    }
}
