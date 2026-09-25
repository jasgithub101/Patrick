package com.patrick.faceid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.patrick.faceid.app.IdentifyOutcome
import com.patrick.faceid.camera.CameraCapture
import com.patrick.faceid.match.Outcome
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun IdentifyScreen(state: IdentifyUiState, vm: IdentifyViewModel) {
    var camera by remember { mutableStateOf<CameraCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val executor = remember { Executors.newSingleThreadExecutor() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Identify Employee",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth().aspectRatio(0.9f).clip(RoundedCornerShape(16.dp))) {
            val error = cameraError
            if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Camera unavailable: " + error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onReady = { camera = it },
                    onError = { cameraError = it.message ?: it::class.simpleName },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val cam = camera
                if (cam != null) {
                    vm.clear()
                    scope.launch {
                        runCatching { cam.capture(executor) }
                            .onSuccess { vm.identify(it) }
                            .onFailure { cameraError = it.message }
                    }
                }
            },
            enabled = !state.busy && camera != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.height(20.dp))
            } else {
                Text("Identify", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (state.busy) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Detect \u2192 Quality \u2192 Align \u2192 Embed \u2192 Search",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val result = state.result
        if (result != null) {
            Spacer(Modifier.height(18.dp))
            ResultCard(result)
            Spacer(Modifier.height(14.dp))
            TechnicalDetails(state, result)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = vm::clear, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        }

        val error = state.error
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Error: " + error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultCard(result: IdentifyOutcome) {
    val label = result.outcome.name
    val (foreground, container) = outcomeColor(label)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = foreground,
            )
            Spacer(Modifier.height(10.dp))

            when (result.outcome) {
                Outcome.MATCH -> {
                    Text(
                        result.personName ?: "-",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        result.dummyAbhaId ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Similarity " + fmtScore(result.bestSimilarity) +
                            "   Margin " + fmtScore(result.margin),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Outcome.UNKNOWN -> {
                    Text(
                        "No registered employee satisfies the recognition criteria.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Best similarity " + fmtScore(result.bestSimilarity),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Outcome.UNCERTAIN -> {
                    Text(
                        result.message
                            ?: "The system could not confidently identify this face.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (result.bestSimilarity != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Best " + fmtScore(result.bestSimilarity) +
                                "   Runner-up " + fmtScore(result.runnerUpSimilarity) +
                                "   Margin " + fmtScore(result.margin),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Processing time " + result.totalMs + " ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TechnicalDetails(state: IdentifyUiState, result: IdentifyOutcome) {
    val status = state.status
    ExpandableSection("Technical Details") {
        if (status != null) {
            DetailRow("Detector", status.detectorName)
            DetailRow("Alignment", status.alignment)
            DetailRow("Embedding model", status.modelVersion)
            DetailRow("Embedding dims", status.embeddingDim.toString())
            DetailRow("Registered people", status.peopleRegistered.toString())
            DetailRow("Stored embeddings", status.embeddingsStored.toString())
        }
        DetailRow("Decision", result.outcome.name + " (" + result.reason.name + ")", emphasis = true)
        DetailRow("Best similarity", fmtScore(result.bestSimilarity), emphasis = true)
        DetailRow("Second best", fmtScore(result.runnerUpSimilarity))
        DetailRow("Margin", fmtScore(result.margin), emphasis = true)
        if (status != null) {
            DetailRow("Match threshold", fmtScore(status.settings.decision.matchThreshold))
            DetailRow("Margin threshold", fmtScore(status.settings.decision.marginThreshold))
            DetailRow("Min quality", fmtScore(status.settings.decision.minQuality))
        }
        DetailRow("Input quality", fmtScore(result.quality))
        val rejection = result.rejection
        if (rejection != null) {
            DetailRow("Rejected because", rejection.name, emphasis = true)
        }
        val metrics = result.metrics
        if (metrics != null) {
            DetailRow("Faces detected", metrics.facesDetected.toString())
            DetailRow("Eye distance px", String.format("%.1f", metrics.interOcularPx))
            DetailRow("Blur variance", String.format("%.1f", metrics.blurVariance))
            DetailRow("Mean luminance", String.format("%.1f", metrics.meanLuminance))
            val yaw = metrics.yaw
            if (yaw != null) {
                DetailRow(
                    "Head yaw/pitch/roll",
                    String.format("%.1f / %.1f / %.1f", yaw, metrics.pitch ?: 0f, metrics.roll ?: 0f),
                )
            }
        }
        DetailRow("Detect ms", result.timings.detectMs.toString())
        DetailRow("Quality ms", result.timings.qualityMs.toString())
        DetailRow("Align ms", result.timings.alignMs.toString())
        DetailRow("Embed ms", result.timings.embedMs.toString())
        DetailRow("Search ms", result.searchMs.toString())
        DetailRow("Total ms", result.totalMs.toString(), emphasis = true)
        Spacer(Modifier.height(8.dp))
        PrototypeDisclaimer(
            "All values are measured from this recognition attempt. Thresholds are experimental " +
                "starting points, not calibrated operating points."
        )
    }
}
