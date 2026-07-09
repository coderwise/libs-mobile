package com.coderwise.libs.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
actual fun rememberBluetoothConnectPermissionState(): BluetoothConnectPermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var status by remember { mutableStateOf(resolveBluetoothConnectStatus(context, activity)) }

    LifecycleResumeEffect(context, activity) {
        status = resolveBluetoothConnectStatus(context, activity)
        onPauseOrDispose { }
    }

    val onResultCallback = remember { mutableStateOf<((PermissionStatus) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val newStatus = resolveBluetoothConnectStatus(context, activity)
        status = newStatus
        onResultCallback.value?.invoke(newStatus)
        onResultCallback.value = null
    }

    return remember(status) {
        object : BluetoothConnectPermissionState {
            override val status: PermissionStatus = status

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    onResult(PermissionStatus.Granted)
                    return
                }
                onResultCallback.value = onResult
                launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }
}

private fun resolveBluetoothConnectStatus(context: Context, activity: Activity?): PermissionStatus {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return PermissionStatus.Granted
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return PermissionStatus.Granted
    val shouldShowRationale = activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.BLUETOOTH_CONNECT)
    return PermissionStatus.Denied(shouldShowRationale)
}
