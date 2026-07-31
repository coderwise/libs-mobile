package com.coderwise.libs.utils

import kotlinx.browser.window

// The browser has no synchronous share API; copy to the clipboard.
actual fun shareText(text: String) = copyToClipboard(text)

internal fun copyToClipboard(text: String) {
    // Absent on insecure origins, so reach for it defensively.
    window.navigator.asDynamic().clipboard?.writeText(text)
    Unit
}
