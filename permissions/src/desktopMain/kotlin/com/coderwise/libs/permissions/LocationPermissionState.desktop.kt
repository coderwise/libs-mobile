package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState = remember {
    object : LocationPermissionState {
        override val status: PermissionStatus = PermissionStatus.Granted
        override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
            onResult(status)
        }
    }
}
