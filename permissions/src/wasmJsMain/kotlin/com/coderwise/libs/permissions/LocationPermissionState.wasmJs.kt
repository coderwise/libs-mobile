package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    val statusState = remember { mutableStateOf<PermissionStatus>(PermissionStatus.Denied(false)) }

    LaunchedEffect(Unit) {
        queryPermissionStatus("geolocation")?.let { statusState.value = it }
    }

    DisposableEffect(Unit) {
        val subscription = subscribeToPermission("geolocation") { statusState.value = it }
        onDispose { subscription() }
    }

    return remember {
        object : LocationPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                // As with the camera, asking is using: a position request is what makes the
                // browser prompt, and the fix itself is thrown away here.
                requestGeolocationAccess(
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

private fun requestGeolocationAccess(onGranted: () -> Unit, onDenied: () -> Unit): Unit = js(
    """{
        if (!navigator.geolocation) { onDenied(); return; }
        navigator.geolocation.getCurrentPosition(() => onGranted(), () => onDenied());
    }"""
)
