package com.patrick.faceid.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.patrick.faceid.face.BitmapImages
import com.patrick.faceid.face.RgbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin CameraX wrapper: a live preview plus a single still capture converted to [RgbImage].
 *
 * Only this class knows about CameraX. The recognition pipeline receives a plain [RgbImage], so it
 * behaves identically for a camera frame and a test fixture.
 */
class CameraCapture(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null

    /** Binds preview + capture to [lifecycleOwner]. Call from the main thread. */
    suspend fun start(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        front: Boolean = true,
    ) {
        // CameraX 1.5.x exposes a ListenableFuture rather than a suspend accessor.
        val cameraProvider = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    {
                        runCatching { future.get() }
                            .onSuccess { continuation.resume(it) }
                            .onFailure { continuation.resumeWithException(it) }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }
        }
        provider = cameraProvider
        val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )
    }

    fun stop() {
        provider?.unbindAll()
        provider = null
        imageCapture = null
    }

    /** Takes one frame. Throws if the camera is not bound or the capture fails. */
    suspend fun capture(executor: Executor): RgbImage {
        val capture = imageCapture ?: error("camera not started")
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(proxy: ImageProxy) {
                    try {
                        continuation.resume(proxy.toRgbImage())
                    } catch (e: Throwable) {
                        continuation.resumeWithException(e)
                    } finally {
                        proxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            })
        }
    }

    /**
     * CameraX hands back a bitmap in sensor orientation. Rotating it upright matters: the detector
     * and the 5-point alignment both assume an upright face, so a sideways frame would align badly
     * and produce a poor embedding.
     */
    private fun ImageProxy.toRgbImage(): RgbImage {
        val bitmap: Bitmap = toBitmap()
        val degrees = imageInfo.rotationDegrees
        val upright = if (degrees == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(degrees.toFloat()) },
                true,
            )
        }
        return try {
            BitmapImages.fromBitmap(upright)
        } finally {
            if (upright !== bitmap) upright.recycle()
            bitmap.recycle()
        }
    }
}
