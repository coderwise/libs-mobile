package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.darwin.NSObject

/**
 * iOS "Always" location access. [PermissionStatus.Granted] means exactly
 * `authorizedAlways` — while-in-use is reported as denied here, because it is
 * precisely the state this permission exists to escalate out of.
 *
 * Requires `NSLocationAlwaysAndWhenInUseUsageDescription` in the host app's
 * Info.plist; without it iOS silently refuses to prompt.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberBackgroundLocationPermissionState(): BackgroundLocationPermissionState {
    val requester = remember { AlwaysAuthRequester() }
    val statusState = remember { mutableStateOf(requester.currentStatus()) }

    DisposableEffect(requester) {
        requester.startObserving { statusState.value = it }
        onDispose { requester.stopObserving() }
    }

    return remember(requester) {
        object : BackgroundLocationPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                val current = requester.currentStatus()
                if (current is PermissionStatus.Granted) {
                    statusState.value = current
                    onResult(current)
                    return
                }
                if (current is PermissionStatus.Denied && !current.shouldShowRationale) {
                    // Already decided, or the one upgrade prompt is spent — either way
                    // re-requesting shows nothing. Report the refusal and let the caller
                    // offer [rememberAppSettingsLauncher] as the way back.
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
 * Owns the one shot iOS gives at upgrading while-in-use to Always.
 *
 * `requestAlwaysAuthorization` is offered **once**: decline it — or dismiss it,
 * or answer "Keep Only While Using" — and every later call is a silent no-op
 * with the status still reading `authorizedWhenInUse`. Nothing in
 * CLAuthorizationStatus separates "not asked yet" from "asked and spent", so
 * [asked] tracks it and [currentStatus] reports a spent while-in-use as a
 * decided refusal. That is what turns a button which would otherwise do nothing
 * forever into one [isDeadEndAfter] can route to Settings.
 *
 * Session-scoped, like the CBCentralManager in
 * [rememberBluetoothConnectPermissionState]: a relaunch grants one more
 * attempt, which iOS answers silently, and the tap after that routes to
 * Settings.
 */
@OptIn(ExperimentalForeignApi::class)
private class AlwaysAuthRequester : NSObject(), CLLocationManagerDelegateProtocol {

    private val manager = CLLocationManager()
    private var pending: ((PermissionStatus) -> Unit)? = null
    private var onStatusChanged: ((PermissionStatus) -> Unit)? = null
    private var asked = false

    fun startObserving(onStatusChanged: (PermissionStatus) -> Unit) {
        this.onStatusChanged = onStatusChanged
        manager.delegate = this
    }

    fun stopObserving() {
        manager.delegate = null
        onStatusChanged = null
    }

    /**
     * The live authorization status, with a spent upgrade prompt folded in as a
     * decided refusal rather than a state the user can still act on.
     */
    fun currentStatus(): PermissionStatus {
        val live = liveStatus()
        val promptSpent = asked && live is PermissionStatus.Denied && live.shouldShowRationale
        return if (promptSpent) PermissionStatus.Denied(shouldShowRationale = false) else live
    }

    fun request(onResult: (PermissionStatus) -> Unit) {
        asked = true
        pending = onResult
        manager.requestAlwaysAuthorization()
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val status = currentStatus()
        onStatusChanged?.invoke(status)
        pending?.invoke(status)
        pending = null
    }

    private fun liveStatus(): PermissionStatus =
        when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedAlways -> PermissionStatus.Granted
            // While-in-use is the state to escalate out of, and not-determined has
            // never been asked — a prompt can still be shown for both.
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusNotDetermined -> PermissionStatus.Denied(shouldShowRationale = true)
            // Denied or restricted — iOS won't prompt again; Settings is the only path.
            else -> PermissionStatus.Denied(shouldShowRationale = false)
        }
}
