package com.patrick.faceid.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrick.faceid.camera.CameraCapture
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(state: RegisterUiState, vm: RegisterViewModel, onDone: () -> Unit) {
    if (state.savedPersonName != null) {
        RegistrationSaved(state, onDone)
        return
    }

    var camera by remember { mutableStateOf<CameraCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val executor = remember { Executors.newSingleThreadExecutor() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Register Employee",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(16.dp))) {
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
        Text(
            state.prompt,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))

        val status = state.statusMessage
        if (status != null) {
            StatusChip(status, state.statusOk)
            Spacer(Modifier.height(8.dp))
        }

        Text(
            "Samples: " + state.samplesTaken + " / " + state.samplesRequired,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        LinearProgressIndicator(
            progress = { state.samplesTaken.toFloat() / state.samplesRequired },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        val quality = state.lastSampleQuality
        if (quality != null) {
            Text(
                "Last sample quality: " + fmtScore(quality),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val cam = camera
                if (cam != null) {
                    scope.launch {
                        runCatching { cam.capture(executor) }
                            .onSuccess { vm.captureSample(it) }
                            .onFailure { cameraError = it.message }
                    }
                }
            },
            enabled = state.canCapture && camera != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.height(20.dp))
            } else {
                Text("Capture sample")
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.employeeName,
            onValueChange = vm::onNameChanged,
            label = { Text("Employee name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        PrototypeDisclaimer(
            "A placeholder ABHA identifier is generated on save; it is not a real health ID."
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.save() },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Save employee") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) {
            Text("Start over")
        }

        val error = state.error
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Error: " + error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    DuplicateDialog(state, vm)
}

@Composable
private fun DuplicateDialog(state: RegisterUiState, vm: RegisterViewModel) {
    val duplicate = state.duplicate ?: return
    AlertDialog(
        onDismissRequest = vm::dismissDuplicate,
        title = { Text("Possibly already registered") },
        text = {
            Column {
                Text(
                    "This face closely matches an existing record. Nothing has been saved, and " +
                        "identities are never merged automatically."
                )
                Spacer(Modifier.height(10.dp))
                DetailRow("Existing name", duplicate.existingDisplayName, emphasis = true)
                DetailRow("Existing ABHA", duplicate.existingDummyAbhaId)
                DetailRow("Similarity", fmtScore(duplicate.similarity), emphasis = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.save(force = true) }) { Text("Different person, save anyway") }
        },
        dismissButton = { TextButton(onClick = vm::dismissDuplicate) { Text("Cancel") } },
    )
}

@Composable
private fun RegistrationSaved(state: RegisterUiState, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("\u2713", style = MaterialTheme.typography.displayMedium, color = OutcomeColors.match)
        Spacer(Modifier.height(10.dp))
        Text(
            "Employee registered",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DetailRow("Name", state.savedPersonName ?: "-", emphasis = true)
                DetailRow("ABHA (dummy)", state.savedDummyAbhaId ?: "-")
                DetailRow("Embeddings stored", state.samplesTaken.toString())
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Done") }
    }
}
