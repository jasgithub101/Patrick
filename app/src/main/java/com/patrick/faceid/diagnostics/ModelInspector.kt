package com.patrick.faceid.diagnostics

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer

/**
 * Milestone-1 diagnostic: loads an ONNX model, records its declared input/output tensors, and
 * runs it once on a zero tensor to record the *runtime* output shapes and timings.
 *
 * The InsightFace models name their outputs numerically, so the detector decoder must bind
 * outputs by index. This report is how that ordering is confirmed rather than assumed.
 *
 * Pure logic: takes model bytes, returns a report. No Android UI types.
 */
object ModelInspector {

    data class TensorSpec(val name: String, val shape: List<Long>, val type: String)

    data class Report(
        val label: String,
        val sizeBytes: Int,
        val inputs: List<TensorSpec>,
        val outputs: List<TensorSpec>,
        val probeInputShape: List<Long>,
        /** Output shapes observed when actually running the model, in output-index order. */
        val runtimeOutputShapes: List<List<Long>>,
        val loadMs: Double,
        val firstRunMs: Double,
        val warmRunMs: Double,
        val error: String? = null,
    ) {
        fun format(): String = buildString {
            appendLine("== $label (${"%.2f".format(sizeBytes / 1e6)} MB) ==")
            if (error != null) {
                appendLine("ERROR: $error")
                return@buildString
            }
            inputs.forEach { appendLine("in   ${it.name} ${it.shape} ${it.type}") }
            outputs.forEachIndexed { i, o ->
                appendLine("out[$i] ${o.name} declared=${o.shape} runtime=${runtimeOutputShapes.getOrNull(i)}")
            }
            appendLine("probe input $probeInputShape")
            appendLine("load %.1f ms | first run %.1f ms | warm run %.1f ms".format(loadMs, firstRunMs, warmRunMs))
        }
    }

    /**
     * @param probeShape concrete input shape to run with (dynamic dims in the model are
     *   replaced by these values).
     */
    fun inspect(env: OrtEnvironment, label: String, modelBytes: ByteArray, probeShape: LongArray): Report {
        var loadMs = 0.0
        return try {
            val t0 = System.nanoTime()
            env.createSession(modelBytes, OrtSession.SessionOptions()).use { session ->
                loadMs = (System.nanoTime() - t0) / 1e6
                val inputs = session.inputInfo.map { (name, info) -> spec(name, info.info) }
                val outputs = session.outputInfo.map { (name, info) -> spec(name, info.info) }

                val inputName = session.inputNames.first()
                val elements = probeShape.fold(1L) { acc, d -> acc * d }.toInt()
                OnnxTensor.createTensor(env, FloatBuffer.allocate(elements), probeShape).use { input ->
                    val feed = mapOf(inputName to input)

                    val r0 = System.nanoTime()
                    val shapes = session.run(feed).use { result ->
                        result.map { (it.value.info as TensorInfo).shape.toList() }
                    }
                    val firstRunMs = (System.nanoTime() - r0) / 1e6

                    val r1 = System.nanoTime()
                    session.run(feed).close()
                    val warmRunMs = (System.nanoTime() - r1) / 1e6

                    Report(label, modelBytes.size, inputs, outputs, probeShape.toList(), shapes,
                        loadMs, firstRunMs, warmRunMs)
                }
            }
        } catch (e: Exception) {
            Report(label, modelBytes.size, emptyList(), emptyList(), probeShape.toList(), emptyList(),
                loadMs, 0.0, 0.0, error = "${e::class.simpleName}: ${e.message}")
        }
    }

    private fun spec(name: String, info: Any): TensorSpec =
        if (info is TensorInfo) TensorSpec(name, info.shape.toList(), info.type.toString())
        else TensorSpec(name, emptyList(), info::class.simpleName ?: "?")
}
