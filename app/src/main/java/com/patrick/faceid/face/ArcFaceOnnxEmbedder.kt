package com.patrick.faceid.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.patrick.faceid.match.VectorMath
import java.nio.FloatBuffer
import java.security.MessageDigest

/**
 * InsightFace MobileFaceNet (w600k_mbf.onnx) on ONNX Runtime.
 *
 * Preprocessing follows insightface v0.7 model_zoo/arcface_onnx.py: RGB (RgbImage is already
 * RGB, so no BGR swap), planar CHW, (px - 127.5) / 127.5. Tensor layout verified on-device in
 * docs/PHASE_1_REPORT.md E1: input "input.1" [-1, 3, 112, 112], single output [1, 512].
 *
 * The model output is NOT unit length, so it is L2-normalised here and cosine similarity
 * becomes a plain dot product downstream.
 *
 * modelVersion embeds the weights' SHA-256 prefix, so the matcher's model-version guard is
 * tied to the exact model file. No android.* imports: runnable from JVM unit tests.
 *
 * Contributed by the user; reviewed and adjusted (TensorInfo casts, interface split,
 * SessionOptions lifetime, reuse of VectorMath).
 */
class ArcFaceOnnxEmbedder(modelBytes: ByteArray) : FaceEmbeddingModel {

    companion object {
        private const val MODEL_NAME = "w600k_mbf"
        private const val INPUT_NAME = "input.1"
        private const val INPUT_WIDTH = 112
        private const val INPUT_HEIGHT = 112
        private const val INPUT_CHANNELS = 3
        private const val OUTPUT_DIM = 512
        private const val INPUT_MEAN = 127.5f
        private const val INPUT_STD = 127.5f
    }

    override val dim: Int = OUTPUT_DIM
    override val modelVersion: String = buildModelVersion(modelBytes)

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        require(modelBytes.isNotEmpty()) { "Face embedding model is empty" }
        val sessionOptions = OrtSession.SessionOptions()
        session = try {
            environment.createSession(modelBytes, sessionOptions)
        } finally {
            sessionOptions.close()
        }
        validateModel()
    }

    private fun buildModelVersion(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hashPrefix = digest.take(4).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "$MODEL_NAME@$hashPrefix"
    }

    override fun embed(image: RgbImage): FloatArray {
        require(image.width == INPUT_WIDTH) { "Expected width $INPUT_WIDTH, got ${image.width}" }
        require(image.height == INPUT_HEIGHT) { "Expected height $INPUT_HEIGHT, got ${image.height}" }

        // Packed per-pixel RGB -> planar CHW: R plane, then G plane, then B plane.
        val planeSize = INPUT_WIDTH * INPUT_HEIGHT
        val input = FloatArray(INPUT_CHANNELS * planeSize)
        for (y in 0 until INPUT_HEIGHT) {
            for (x in 0 until INPUT_WIDTH) {
                val i = y * INPUT_WIDTH + x
                input[i] = (image.red(x, y) - INPUT_MEAN) / INPUT_STD
                input[planeSize + i] = (image.green(x, y) - INPUT_MEAN) / INPUT_STD
                input[2 * planeSize + i] = (image.blue(x, y) - INPUT_MEAN) / INPUT_STD
            }
        }

        val shape = longArrayOf(1L, INPUT_CHANNELS.toLong(), INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong())
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { inputTensor ->
            session.run(mapOf(INPUT_NAME to inputTensor)).use { results ->
                val embedding = extractEmbedding(results[0].value)
                require(embedding.size == OUTPUT_DIM) {
                    "Expected $OUTPUT_DIM embedding values, got ${embedding.size}"
                }
                return VectorMath.l2Normalize(embedding)
            }
        }
    }

    private fun validateModel() {
        val inputNode = session.inputInfo[INPUT_NAME]
            ?: throw IllegalStateException("ONNX model does not contain input '$INPUT_NAME'")
        val inputInfo = inputNode.info as? TensorInfo
            ?: throw IllegalStateException("Input '$INPUT_NAME' is not a tensor")
        val inputShape = inputInfo.shape
        require(inputShape.size == 4) { "Expected 4D input, got ${inputShape.contentToString()}" }
        // Batch dimension intentionally unchecked: the verified model reports [-1, 3, 112, 112].
        require(inputShape[1] == INPUT_CHANNELS.toLong()) { "Expected 3 channels, got ${inputShape[1]}" }
        require(inputShape[2] == INPUT_HEIGHT.toLong()) { "Expected height $INPUT_HEIGHT, got ${inputShape[2]}" }
        require(inputShape[3] == INPUT_WIDTH.toLong()) { "Expected width $INPUT_WIDTH, got ${inputShape[3]}" }

        require(session.outputInfo.size == 1) { "Expected one output, got ${session.outputInfo.size}" }
        val outputInfo = session.outputInfo.values.first().info as? TensorInfo
            ?: throw IllegalStateException("Model output is not a tensor")
        val outputShape = outputInfo.shape
        require(outputShape.size == 2) { "Expected 2D output, got ${outputShape.contentToString()}" }
        require(outputShape[1] == OUTPUT_DIM.toLong()) {
            "Expected output dimension $OUTPUT_DIM, got ${outputShape[1]}"
        }
    }

    private fun extractEmbedding(value: Any): FloatArray = when (value) {
        is Array<*> -> {
            require(value.size == 1) { "Expected batch size 1, got ${value.size}" }
            when (val row = value[0]) {
                is FloatArray -> row.copyOf()
                is Array<*> -> FloatArray(row.size) { (row[it] as Number).toFloat() }
                else -> throw IllegalStateException("Unexpected output row type: ${row?.javaClass?.name}")
            }
        }
        is FloatArray -> value.copyOf()
        else -> throw IllegalStateException("Unexpected ONNX output type: ${value.javaClass.name}")
    }

    override fun close() {
        session.close()
    }
}
