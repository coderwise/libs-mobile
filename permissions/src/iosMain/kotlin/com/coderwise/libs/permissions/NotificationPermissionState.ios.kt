package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberNotificationPermissionState(): NotificationPermissionState = remember {
    object : NotificationPermissionState {
        override val status: PermissionStatus = PermissionStatus.Granted
        override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) = onResult(status)
    }
}
