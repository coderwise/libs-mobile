package com.coderwise.libs.utils

internal actual fun copyToClipboard(text: String) {
    writeToClipboard(text)
}

// Both guards live inside the JS: `navigator.clipboard` is absent on insecure origins, and the
// write is refused without a user gesture or the clipboard-write permission. wasmJs has no
// `dynamic` to make that call optional from the Kotlin side, and a best-effort copy has nothing
// to report back either way.
private fun writeToClipboard(text: String): Unit = js(
    "{ if (navigator.clipboard) navigator.clipboard.writeText(text).catch(() => {}); }"
)
