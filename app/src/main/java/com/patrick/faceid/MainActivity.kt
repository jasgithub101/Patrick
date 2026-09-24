package com.patrick.faceid

import ai.onnxruntime.OrtEnvironment
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.patrick.faceid.diagnostics.ModelInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Milestone 1: prove ONNX Runtime loads both bundled models on-device and report their tensor
 * layout. Replaced by the real Register/Identify UI in later milestones.
 */
class MainActivity : ComponentActivity() {

    private var report by mutableStateOf("Inspecting models…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Text(
                        text = report,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        }

        lifecycleScope.launch {
            report = withContext(Dispatchers.Default) { inspectModels() }
            report.lines().forEach { Log.i(TAG, it) }
        }
    }

    private fun inspectModels(): String {
        val env = OrtEnvironment.getEnvironment()
        val probes = listOf(
            Triple("detector", "models/det_500m.onnx", longArrayOf(1, 3, 640, 640)),
            Triple("embedder", "models/w600k_mbf.onnx", longArrayOf(1, 3, 112, 112)),
        )
        return probes.joinToString("\n") { (label, asset, shape) ->
            val bytes = assets.open(asset).use { it.readBytes() }
            ModelInspector.inspect(env, "$label: $asset", bytes, shape).format()
        }
    }

    private companion object {
        const val TAG = "ModelInspector"
    }
}
