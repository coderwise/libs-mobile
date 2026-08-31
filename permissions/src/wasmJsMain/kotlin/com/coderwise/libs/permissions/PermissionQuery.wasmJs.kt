package com.coderwise.libs.permissions

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The browser half of the permission states, in one place because camera and geolocation
 * differ only in the name they are queried under and in what "asking" means for them.
 *
 * wasmJs has no `dynamic`, so the Permissions API is reached through js() functions that take
 * Kotlin callbacks: everything crossing the boundary is a String or a callback, and nothing
 * needs a typed binding for the result objects.
 */
internal fun mapPermissionState(state: String): PermissionStatus = when (state) {
    "granted" -> PermissionStatus.Granted
    else -> PermissionStatus.Denied(false)
}

/** The Permissions API's current answer, or null where the browser does not answer at all. */
internal suspend fun queryPermissionStatus(name: String): PermissionStatus? =
    suspendCancellableCoroutine { continuation ->
        queryPermission(
            name = name,
            onState = { state -> if (continuation.isActive) continuation.resume(mapPermissionState(state)) },
            onUnavailable = { if (continuation.isActive) continuation.resume(null) }
        )
    }

/** Watches the permission for changes made outside the page; returns the unsubscribe call. */
internal fun subscribeToPermission(name: String, onChange: (PermissionStatus) -> Unit): () -> Unit {
    val subscription = watchPermission(name) { state -> onChange(mapPermissionState(state)) }
    return { disposeSubscription(subscription) }
}

private fun queryPermission(
    name: String,
    onState: (String) -> Unit,
    onUnavailable: () -> Unit
): Unit = js(
    """{
        if (!navigator.permissions) { onUnavailable(); return; }
        navigator.permissions.query({ name: name }).then(
            (result) => onState(result.state),
            () => onUnavailable()
        );
    }"""
)

// The subscription is a JS object rather than a Kotlin lambda: the query resolves later, so
// the listener to remove is not known until after this returns, and JS is where that state
// can live.
private fun watchPermission(name: String, onState: (String) -> Unit): JsAny = js(
    """{
        if (!navigator.permissions) return { dispose: () => {} };
        let handle = null;
        let disposed = false;
        const listener = () => onState(handle.state);
        navigator.permissions.query({ name: name }).then((result) => {
            if (disposed) return;
            handle = result;
            result.addEventListener('change', listener);
        }, () => {});
        return {
            dispose: () => {
                disposed = true;
                if (handle) handle.removeEventListener('change', listener);
            }
        };
    }"""
)

private fun disposeSubscription(subscription: JsAny): Unit = js("subscription.dispose()")
