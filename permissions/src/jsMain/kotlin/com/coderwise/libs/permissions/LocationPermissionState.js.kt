package com.coderwise.libs.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberLocationPermissionState(): LocationPermissionState {
    val statusState = remember { mutableStateOf<PermissionStatus>(PermissionStatus.Denied(false)) }

    LaunchedEffect(Unit) {
        queryGeolocationStatus()?.let { statusState.value = it }
    }

    DisposableEffect(Unit) {
        val subscription = subscribeToGeolocationStatus { statusState.value = it }
        onDispose { subscription() }
    }

    return remember {
        object : LocationPermissionState {
            override val status: PermissionStatus
                get() = statusState.value

            override fun launchPermissionRequest(onResult: (PermissionStatus) -> Unit) {
                val geolocation = js("navigator.geolocation")
                if (geolocation == null || geolocation == undefined) {
                    val status = PermissionStatus.Denied(false)
                    statusState.value = status
                    onResult(status)
                    return
                }
                val onSuccess: (dynamic) -> Unit = {
                    val status = PermissionStatus.Granted
                    statusState.value = status
                    onResult(status)
                }
                val onError: (dynamic) -> Unit = {
                    val status = PermissionStatus.Denied(false)
                    statusState.value = status
                    onResult(status)
                }
                geolocation.getCurrentPosition(onSuccess, onError)
            }
        }
    }
}

private suspend fun queryGeolocationStatus(): PermissionStatus? {
    val permissions = js("navigator.permissions")
    if (permissions == null || permissions == undefined) return null
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val query = js("({ name: 'geolocation' })")
        val promise = permissions.query(query)
        promise.then({ result: dynamic ->
            cont.resumeWith(Result.success(mapState(result.state as String)))
        }, { _: dynamic ->
            cont.resumeWith(Result.success(null))
        })
    }
}

private fun subscribeToGeolocationStatus(onChange: (PermissionStatus) -> Unit): () -> Unit {
    val permissions = js("navigator.permissions")
    if (permissions == null || permissions == undefined) return {}
    var disposed = false
    var cleanup: (() -> Unit)? = null
    val query = js("({ name: 'geolocation' })")
    permissions.query(query).then({ result: dynamic ->
        if (disposed) return@then
        val listener: (dynamic) -> Unit = {
            onChange(mapState(result.state as String))
        }
        result.addEventListener("change", listener)
        cleanup = { result.removeEventListener("change", listener) }
    }, { _: dynamic -> })
    return {
        disposed = true
        cleanup?.invoke()
    }
}

private fun mapState(state: String): PermissionStatus = when (state) {
    "granted" -> PermissionStatus.Granted
    else -> PermissionStatus.Denied(false)
}
