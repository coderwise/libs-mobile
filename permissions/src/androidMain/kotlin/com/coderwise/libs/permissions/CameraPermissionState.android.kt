package com.coderwise.libs.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
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

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val statusState = remember { mutableStateOf(resolveCameraStatus(context, activity)) }

    // The user may have granted access in the settings app and walked back in.
    LifecycleResumeEffect(context, activity) {
        statusState.value = resolveCameraStatus(context, activity)
        onPauseOrDispose { }
    }

    val onResultCallback = remember { mutableStateOf<((PermissionStatus) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val newStatus = resolveCameraStatus(context, activity)
        statusState.value = newStatus
        onResultCallback.value?.invoke(newStatus)
        onResultCallback.value = null
    }

    return remember(launcher) {
        object : CameraPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                onResultCallback.value = onResult
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

private fun resolveCameraStatus(context: Context, activity: Activity?): PermissionStatus {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return PermissionStatus.Granted
    val shouldShowRationale = activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    return PermissionStatus.Denied(shouldShowRationale)
}
