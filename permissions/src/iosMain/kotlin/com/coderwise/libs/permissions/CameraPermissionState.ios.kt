package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val statusState = remember { mutableStateOf(currentCameraStatus()) }

    // AVFoundation has nothing to observe — unlike CoreLocation there is no delegate for an
    // authorization change — so a grant made in the Settings app would otherwise never reach the
    // caller. Coming back to the front is when that can have happened; it is also what the Android
    // side already covers with its resume check. Delivered on the main queue: this is snapshot state.
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            statusState.value = currentCameraStatus()
        }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }

    return remember {
        object : CameraPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                val current = statusState.value
                if (current is PermissionStatus.Granted) {
                    onResult(current)
                    return
                }
                if (current is PermissionStatus.Denied && !current.shouldShowRationale) {
                    // iOS asks once and once only — a second requestAccess call would silently
                    // no-op, so report the refusal rather than pretend to ask.
                    // [rememberAppSettingsLauncher] is the only remaining way back.
                    onResult(current)
                    return
                }

                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                    // The answer arrives on an arbitrary queue; snapshot state belongs to the main one.
                    dispatch_async(dispatch_get_main_queue()) {
                        val newStatus = currentCameraStatus()
                        statusState.value = newStatus
                        onResult(newStatus)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun currentCameraStatus(): PermissionStatus =
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
        // Not yet decided — a system prompt can still be shown.
        AVAuthorizationStatusNotDetermined -> PermissionStatus.Denied(shouldShowRationale = true)
        // Denied or restricted — iOS won't prompt again; Settings is the only path.
        else -> PermissionStatus.Denied(shouldShowRationale = false)
    }
