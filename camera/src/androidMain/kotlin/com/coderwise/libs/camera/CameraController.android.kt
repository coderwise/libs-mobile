package com.coderwise.libs.camera

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
actual fun rememberCameraController(lens: CameraLens): CameraController {
    // A preview has no sensor to bind and no Activity to draw into; the stub renders the same
    // "no camera" path the tooling can actually draw.
    if (LocalInspectionMode.current) return UnsupportedCameraController

    val context = LocalContext.current
    val hasCamera = remember(context, lens) {
        val feature = when (lens) {
            CameraLens.Back -> PackageManager.FEATURE_CAMERA
            CameraLens.Front -> PackageManager.FEATURE_CAMERA_FRONT
        }
        context.packageManager.hasSystemFeature(feature)
    }

    val previewUseCase = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    return remember(previewView, imageCapture, hasCamera, lens) {
        CameraXController(
            isSupported = hasCamera,
            lens = lens,
            previewUseCase = previewUseCase,
            previewView = previewView,
            imageCapture = imageCapture,
        )
    }
}

private class CameraXController(
    override val isSupported: Boolean,
    private val lens: CameraLens,
    private val previewUseCase: Preview,
    private val previewView: PreviewView,
    private val imageCapture: ImageCapture,
) : CameraController {

    /**
     * Callbacks land on the main thread, where the results are handed back to the composition. Off
     * the preview's own context, which is the one this controller was built in.
     */
    private val executor: Executor = ContextCompat.getMainExecutor(previewView.context)

    /**
     * Writes to a stream rather than reading the sensor buffer directly: on the way out CameraX
     * records the device's rotation as EXIF, so the bytes arrive the right way up without this
     * class tracking the display orientation.
     */
    override suspend fun capture(): Result<CapturedImage> = suspendCancellableCoroutine { cont ->
        val stream = ByteArrayOutputStream()
        val options = ImageCapture.OutputFileOptions.Builder(stream).build()
        imageCapture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(Result.success(CapturedImage(stream.toByteArray())))
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resume(Result.failure(exception))
                }
            },
        )
    }

    @Composable
    override fun Viewfinder(modifier: Modifier) {
        BindCamera(
            context = previewView.context,
            lifecycleOwner = LocalLifecycleOwner.current,
            lens = lens,
            previewUseCase = previewUseCase,
            previewView = previewView,
            imageCapture = imageCapture,
        )
        AndroidView(factory = { previewView }, modifier = modifier)
    }
}

/**
 * Holds the sensor for exactly as long as the viewfinder is composed. `bindToLifecycle` alone would
 * keep it open after the preview is gone, because the lifecycle it binds to belongs to the Activity,
 * and the Activity outlives a screen inside it.
 */
@Composable
private fun BindCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    lens: CameraLens,
    previewUseCase: Preview,
    previewView: PreviewView,
    imageCapture: ImageCapture,
) {
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, previewView, lens) {
        var provider: ProcessCameraProvider? = null
        val job = scope.launch {
            val cameraProvider = ProcessCameraProvider.awaitInstance(context)
            provider = cameraProvider
            previewUseCase.surfaceProvider = previewView.surfaceProvider
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                when (lens) {
                    CameraLens.Back -> CameraSelector.DEFAULT_BACK_CAMERA
                    CameraLens.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
                },
                previewUseCase,
                imageCapture,
            )
        }
        onDispose {
            job.cancel()
            provider?.unbindAll()
        }
    }
}
