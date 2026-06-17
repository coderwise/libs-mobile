package com.coderwise.libs.utils

import kotlinx.browser.window

// The browser has no synchronous share API; copy to the clipboard.
actual fun shareText(text: String) {
    window.navigator.asDynamic().clipboard?.writeText(text)
}
