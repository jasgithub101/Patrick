package com.patrick.faceid.face

/**
 * Turns an aligned 112x112 face into an L2-normalised embedding.
 * Kept as an interface so the model can be replaced (Phase 5 model comparison).
 */
interface FaceEmbeddingModel : AutoCloseable {
    /** Stored with every embedding; embeddings from different versions are never compared. */
    val modelVersion: String
    val dim: Int
    fun embed(image: RgbImage): FloatArray
}
