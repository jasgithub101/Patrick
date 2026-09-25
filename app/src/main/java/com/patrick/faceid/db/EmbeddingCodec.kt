package com.patrick.faceid.db

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts embeddings between FloatArray and the little-endian float32 BLOB stored in SQLite.
 * Byte order is fixed explicitly so a database file stays readable across devices.
 */
object EmbeddingCodec {

    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) { "blob size ${bytes.size} is not a multiple of 4" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.getFloat() }
    }
}
