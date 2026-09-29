package com.coderwise.libs.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.coroutines.resume
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCameraController(lens: CameraLens): CameraController {
    // Nil on the simulator and on any device without a camera facing [lens], which is what makes
    // the caller offer an alternative instead of a dead shutter.
    val device = remember(lens) { deviceFacing(lens) }
    val session = remember(device) { device?.let(::buildSession) }
    val previewView = remember(session) { session?.let { CameraPreviewView(it) } }

    return remember(session, previewView) {
        AVCameraController(
            isSupported = device != null,
            session = session,
            previewView = previewView,
        )
    }
}

/**
 * Runs the capture session for exactly as long as the viewfinder is composed. `startRunning` blocks
 * until the hardware is ready, so it is never called on the main thread.
 */
@Composable
private fun RunSession(session: AVCaptureSession?) {
    DisposableEffect(session) {
        if (session != null) {
            dispatchBackground { session.startRunning() }
        }
        onDispose {
            if (session != null && session.isRunning()) {
                dispatchBackground { session.stopRunning() }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class AVCameraController(
    override val isSupported: Boolean,
    private val session: AVCaptureSession?,
    private val previewView: CameraPreviewView?,
) : CameraController {

    /**
     * Held for the length of the shot. AVFoundation keeps only a weak reference to the delegate, so
     * a local would be collected between pressing the shutter and the photo arriving.
     */
    private var pendingShot: PhotoCaptureDelegate? = null

    /** The session's own output, so it cannot be handed one belonging to a different session. */
    private val output: AVCapturePhotoOutput? = session?.let(::photoOutputOf)

    override suspend fun capture(): Result<CapturedImage> = suspendCancellableCoroutine { cont ->
        val photoOutput = output
        if (photoOutput == null || session?.isRunning() != true) {
            cont.resume(Result.failure(IllegalStateException("The camera is not running")))
            return@suspendCancellableCoroutine
        }
        val delegate = PhotoCaptureDelegate { result ->
            pendingShot = null
            if (cont.isActive) cont.resume(result)
        }
        pendingShot = delegate
        photoOutput.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), delegate)
    }

    /**
     * Non-interactive on purpose: the feed is something to look at, and the chrome drawn over it —
     * the shutter above all — must keep receiving the taps.
     */
    @Composable
    override fun Viewfinder(modifier: Modifier) {
        val view = previewView ?: return
        RunSession(session)
        UIKitView(
            factory = { view },
            modifier = modifier,
            properties = UIKitInteropProperties(interactionMode = null),
        )
    }
}

/**
 * The preview layer is not a view and does not follow its parent's size, so the container resizes it
 * on every layout pass — otherwise the feed keeps whatever bounds it had when it was created.
 */
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView(session: AVCaptureSession) : UIView(frame = ZeroRect) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoCaptureDelegate(
    private val onResult: (Result<CapturedImage>) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        val bytes = didFinishProcessingPhoto.fileDataRepresentation()?.toByteArray()
        val result = when {
            error != null -> Result.failure(IllegalStateException(error.localizedDescription))
            bytes == null -> Result.failure(IllegalStateException("The photo had no data"))
            else -> Result.success(CapturedImage(bytes))
        }
        onResult(result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun buildSession(device: AVCaptureDevice): AVCaptureSession? {
    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return null
    val session = AVCaptureSession()
    session.beginConfiguration()
    session.sessionPreset = AVCaptureSessionPresetPhoto
    if (session.canAddInput(input)) session.addInput(input)
    session.addOutput(AVCapturePhotoOutput())
    session.commitConfiguration()
    return session
}

private fun deviceFacing(lens: CameraLens): AVCaptureDevice? {
    val position = when (lens) {
        CameraLens.Back -> AVCaptureDevicePositionBack
        CameraLens.Front -> AVCaptureDevicePositionFront
    }
    return AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
        deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
        mediaType = AVMediaTypeVideo,
        position = position,
    ).devices.firstOrNull() as? AVCaptureDevice
}

private fun photoOutputOf(session: AVCaptureSession): AVCapturePhotoOutput? =
    session.outputs.filterIsInstance<AVCapturePhotoOutput>().firstOrNull()

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private val ZeroRect: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0)

private fun dispatchBackground(block: () -> Unit) {
    dispatch_async(
        dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL),
        block,
    )
}
