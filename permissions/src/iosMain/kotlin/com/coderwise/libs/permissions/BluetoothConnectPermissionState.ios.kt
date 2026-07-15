package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberBluetoothConnectPermissionState(): BluetoothConnectPermissionState {
    val statusState = remember { mutableStateOf(currentBluetoothStatus()) }
    val requester = remember { BluetoothAuthRequester { statusState.value = it } }

    return remember {
        object : BluetoothConnectPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                val current = currentBluetoothStatus()
                if (current is PermissionStatus.Granted) {
                    statusState.value = current
                    onResult(current)
                    return
                }
                if (current is PermissionStatus.Denied && !current.shouldShowRationale) {
                    // Already decided (denied/restricted) — re-requesting is a
                    // silent no-op, so send the user to Settings instead.
                    openIosAppSettings()
                    statusState.value = current
                    onResult(current)
                    return
                }
                requester.request(onResult)
            }
        }
    }
}

/**
 * There is no explicit request API in CoreBluetooth: instantiating a
 * [CBCentralManager] is what triggers the system Bluetooth prompt (requires
 * NSBluetoothAlwaysUsageDescription in the host app's Info.plist). The manager
 * is created lazily on the first request so composition alone never prompts,
 * and kept alive so the delegate outlives the prompt.
 */
private class BluetoothAuthRequester(
    private val onStatusChanged: (PermissionStatus) -> Unit
) : NSObject(), CBCentralManagerDelegateProtocol {
    private var manager: CBCentralManager? = null
    private var pending: ((PermissionStatus) -> Unit)? = null

    fun request(onResult: (PermissionStatus) -> Unit) {
        pending = onResult
        if (manager == null) {
            manager = CBCentralManager(
                delegate = this,
                queue = dispatch_get_main_queue(),
                options = mapOf<Any?, Any>(CBCentralManagerOptionShowPowerAlertKey to false)
            )
        } else {
            // Prompt already resolved on a previous request; report as-is.
            deliverIfDetermined()
        }
    }

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        deliverIfDetermined()
    }

    private fun deliverIfDetermined() {
        if (CBManager.authorization == CBManagerAuthorizationNotDetermined) return
        val status = currentBluetoothStatus()
        onStatusChanged(status)
        pending?.invoke(status)
        pending = null
    }
}

private fun currentBluetoothStatus(): PermissionStatus =
    when (CBManager.authorization) {
        CBManagerAuthorizationAllowedAlways -> PermissionStatus.Granted
        // Not yet decided — a system prompt can still be shown.
        CBManagerAuthorizationNotDetermined -> PermissionStatus.Denied(shouldShowRationale = true)
        // Denied or restricted — iOS won't prompt again; Settings is the only path.
        else -> PermissionStatus.Denied(shouldShowRationale = false)
    }
