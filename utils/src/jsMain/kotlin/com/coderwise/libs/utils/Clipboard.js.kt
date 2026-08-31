package com.coderwise.libs.utils

import kotlinx.browser.window

internal actual fun copyToClipboard(text: String) {
    // Absent on insecure origins, and refused without a user gesture or the clipboard-write
    // permission — so reach for it defensively, and let a refusal pass rather than surface as an
    // unhandled rejection. A best-effort copy is what the API promises here.
    val clipboard = window.navigator.asDynamic().clipboard ?: return
    clipboard.writeText(text).catch({ _: dynamic -> })
    Unit
}
