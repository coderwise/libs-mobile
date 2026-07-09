package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberBluetoothConnectPermissionState(): BluetoothConnectPermissionState = remember {
    object : BluetoothConnectPermissionState {
        override val status: PermissionStatus = PermissionStatus.Denied(shouldShowRationale = false)
        override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) = onResult(status)
    }
}
