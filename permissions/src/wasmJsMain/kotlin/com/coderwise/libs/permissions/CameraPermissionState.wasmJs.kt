package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val statusState = remember { mutableStateOf<PermissionStatus>(PermissionStatus.Denied(false)) }

    LaunchedEffect(Unit) {
        queryPermissionStatus("camera")?.let { statusState.value = it }
    }

    DisposableEffect(Unit) {
        val subscription = subscribeToPermission("camera") { statusState.value = it }
        onDispose { subscription() }
    }

    return remember {
        object : CameraPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                // No standalone "request access" API — opening a stream is the request, so the
                // permission answer arrives with it. The stream itself is of no use here.
                requestCameraAccess(
                    onGranted = {
                        statusState.value = PermissionStatus.Granted
                        onResult(PermissionStatus.Granted)
                    },
                    onDenied = {
                        val status = PermissionStatus.Denied(false)
                        statusState.value = status
                        onResult(status)
                    }
                )
            }
        }
    }
}

private fun requestCameraAccess(onGranted: () -> Unit, onDenied: () -> Unit): Unit = js(
    """{
        if (!navigator.mediaDevices) { onDenied(); return; }
        navigator.mediaDevices.getUserMedia({ video: true }).then(
            (stream) => { stream.getTracks().forEach((track) => track.stop()); onGranted(); },
            () => onDenied()
        );
    }"""
)
