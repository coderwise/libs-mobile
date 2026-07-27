package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val statusState = remember { mutableStateOf<PermissionStatus>(PermissionStatus.Denied(false)) }

    LaunchedEffect(Unit) {
        queryCameraStatus()?.let { statusState.value = it }
    }

    DisposableEffect(Unit) {
        val subscription = subscribeToCameraStatus { statusState.value = it }
        onDispose { subscription() }
    }

    return remember {
        object : CameraPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                val mediaDevices = js("navigator.mediaDevices")
                if (mediaDevices == null || mediaDevices == undefined) {
                    val status = PermissionStatus.Denied(false)
                    statusState.value = status
                    onResult(status)
                    return
                }
                // No standalone "request access" API — opening a stream is the request, so the
                // permission answer arrives with it. The stream itself is of no use here.
                val constraints = js("({ video: true })")
                val onSuccess: (dynamic) -> Unit = { stream ->
                    stream.getTracks().forEach { track: dynamic -> track.stop() }
                    val status = PermissionStatus.Granted
                    statusState.value = status
                    onResult(status)
                }
                val onError: (dynamic) -> Unit = {
                    val status = PermissionStatus.Denied(false)
                    statusState.value = status
                    onResult(status)
                }
                mediaDevices.getUserMedia(constraints).then(onSuccess, onError)
            }
        }
    }
}

private suspend fun queryCameraStatus(): PermissionStatus? {
    val permissions = js("navigator.permissions")
    if (permissions == null || permissions == undefined) return null
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val query = js("({ name: 'camera' })")
        val promise = permissions.query(query)
        promise.then({ result: dynamic ->
            cont.resumeWith(Result.success(mapCameraState(result.state as String)))
        }, { _: dynamic ->
            cont.resumeWith(Result.success(null))
        })
    }
}

private fun subscribeToCameraStatus(onChange: (PermissionStatus) -> Unit): () -> Unit {
    val permissions = js("navigator.permissions")
    if (permissions == null || permissions == undefined) return {}
    var disposed = false
    var cleanup: (() -> Unit)? = null
    val query = js("({ name: 'camera' })")
    permissions.query(query).then({ result: dynamic ->
        if (disposed) return@then
        val listener: (dynamic) -> Unit = {
            onChange(mapCameraState(result.state as String))
        }
        result.addEventListener("change", listener)
        cleanup = { result.removeEventListener("change", listener) }
    }, { _: dynamic -> })
    return {
        disposed = true
        cleanup?.invoke()
    }
}

private fun mapCameraState(state: String): PermissionStatus = when (state) {
    "granted" -> PermissionStatus.Granted
    else -> PermissionStatus.Denied(false)
}
