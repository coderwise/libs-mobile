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
actual fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var status by remember { mutableStateOf(resolveNotificationStatus(context, activity)) }

    LifecycleResumeEffect(context, activity) {
        status = resolveNotificationStatus(context, activity)
        onPauseOrDispose { }
    }

    val onResultCallback = remember { mutableStateOf<((PermissionStatus) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val newStatus = resolveNotificationStatus(context, activity)
        status = newStatus
        onResultCallback.value?.invoke(newStatus)
        onResultCallback.value = null
    }

    return remember(status) {
        object : NotificationPermissionState {
            override val status: PermissionStatus = status

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    onResult(PermissionStatus.Granted)
                    return
                }
                onResultCallback.value = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private fun resolveNotificationStatus(context: Context, activity: Activity?): PermissionStatus {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionStatus.Granted
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return PermissionStatus.Granted
    val shouldShowRationale = activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    return PermissionStatus.Denied(shouldShowRationale)
}
