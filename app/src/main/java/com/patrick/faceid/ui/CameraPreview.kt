package com.patrick.faceid.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.patrick.faceid.camera.CameraCapture

/**
 * Live camera preview. Hands the bound [CameraCapture] back through [onReady] so the caller can
 * take a still; the caller owns when a capture happens.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onReady: (CameraCapture) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val capture = remember { CameraCapture(context) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(Unit) {
        runCatching { capture.start(lifecycleOwner, previewView.surfaceProvider, front = true) }
            .onSuccess { onReady(capture) }
            .onFailure(onError)
    }
    DisposableEffect(Unit) { onDispose { capture.stop() } }

    Box(modifier) { AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize()) }
}
