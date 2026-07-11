package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * BLUETOOTH_CONNECT — needed to read bonded/paired-device info and connection
 * state on API 31+. Below that it's covered by the legacy install-time
 * BLUETOOTH permission, so [status] is always [PermissionStatus.Granted].
 * On iOS this maps to CoreBluetooth authorization (the host app must declare
 * NSBluetoothAlwaysUsageDescription in Info.plist). No equivalent concept on
 * desktop/js today — [status] is always [PermissionStatus.Denied] there
 * (no platform support, not a deniable prompt).
 */
@Stable
interface BluetoothConnectPermissionState {
    val status: PermissionStatus
    fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit = {})
}

@Composable
expect fun rememberBluetoothConnectPermissionState(): BluetoothConnectPermissionState
