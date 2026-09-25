package com.patrick.faceid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.patrick.faceid.app.EngineStatus

@Composable
fun HomeScreen(
    status: EngineStatus?,
    onRegister: () -> Unit,
    onIdentify: () -> Unit,
    onPeople: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "FaceID Demo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Phase 1 Prototype",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "On-device face recognition. Nothing leaves this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onRegister,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Register Employee", style = MaterialTheme.typography.titleMedium) }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onIdentify,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) { Text("Recognise Employee", style = MaterialTheme.typography.titleMedium) }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onPeople,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                text = if (status == null) "Registered Employees" else
                    "Registered Employees (" + status.peopleRegistered + ")",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.height(28.dp))
        if (status != null) {
            ExpandableSection("Technical Details") {
                DetailRow("Detector", status.detectorName)
                DetailRow("Alignment", status.alignment)
                DetailRow("Embedding model", status.modelVersion)
                DetailRow("Embedding dims", status.embeddingDim.toString())
                DetailRow("Registered people", status.peopleRegistered.toString())
                DetailRow("Stored embeddings", status.embeddingsStored.toString())
                DetailRow("Match threshold", fmtScore(status.settings.decision.matchThreshold))
                DetailRow("Margin threshold", fmtScore(status.settings.decision.marginThreshold))
                DetailRow("Min quality", fmtScore(status.settings.decision.minQuality))
                DetailRow("Images per person", status.settings.registration.imagesPerPerson.toString())
                Spacer(Modifier.height(8.dp))
                PrototypeDisclaimer(
                    "Thresholds are experimental starting values, not calibrated operating points. " +
                        "Model weights are licensed for non-commercial research only."
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PrototypeDisclaimer(
            "Demonstration prototype. ABHA identifiers are placeholders prefixed DUMMY- and are " +
                "not real health IDs."
        )
    }
}
