package com.coderwise.libs.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val statusState = remember { mutableStateOf(resolveStatus(context, activity)) }

    // Refresh status when activity is resumed (in case user changed it in settings)
    LifecycleResumeEffect(context, activity) {
        statusState.value = resolveStatus(context, activity)
        onPauseOrDispose { }
    }

    val onResultCallback = remember { mutableStateOf<((PermissionStatus) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val newStatus = resolveStatus(context, activity)
        statusState.value = newStatus
        onResultCallback.value?.invoke(newStatus)
        onResultCallback.value = null
    }

    return remember(launcher) {
        object : LocationPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                onResultCallback.value = onResult
                launcher.launch(LOCATION_PERMISSIONS)
            }
        }
    }
}

private fun resolveStatus(context: Context, activity: Activity?): PermissionStatus {
    val granted = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    if (granted) return PermissionStatus.Granted
    val shouldShowRationale = activity != null && LOCATION_PERMISSIONS.any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
    return PermissionStatus.Denied(shouldShowRationale)
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
