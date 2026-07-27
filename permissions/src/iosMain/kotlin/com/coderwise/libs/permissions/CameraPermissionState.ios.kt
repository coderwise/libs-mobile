package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val statusState = remember { mutableStateOf(currentCameraStatus()) }

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
                    // no-op, so Settings is the only remaining way back.
                    openIosAppSettings()
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
