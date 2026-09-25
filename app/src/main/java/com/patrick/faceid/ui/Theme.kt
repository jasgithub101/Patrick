package com.patrick.faceid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00695C)
private val TealLight = Color(0xFF4DB6AC)

/** Outcome colours, used consistently wherever a MATCH / UNCERTAIN / UNKNOWN is shown. */
object OutcomeColors {
    val match = Color(0xFF1B5E20)
    val matchContainer = Color(0xFFDCEDC8)
    val uncertain = Color(0xFF8D6E00)
    val uncertainContainer = Color(0xFFFFF3C4)
    val unknown = Color(0xFF6B1D1D)
    val unknownContainer = Color(0xFFFADBD8)
}

@Composable
fun FaceIdTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(primary = TealLight, secondary = TealLight)
        } else {
            lightColorScheme(primary = Teal, secondary = Teal)
        },
        content = content,
    )
}
