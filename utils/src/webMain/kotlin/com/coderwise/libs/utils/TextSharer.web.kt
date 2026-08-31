package com.coderwise.libs.utils

// The browser has no synchronous share API; copy to the clipboard.
actual fun shareText(text: String) = copyToClipboard(text)

/**
 * The clipboard write itself, which is the one part the two web targets cannot share: js
 * reaches an optional `navigator.clipboard` through `dynamic`, and wasmJs has no `dynamic`
 * to do that with.
 */
internal expect fun copyToClipboard(text: String)
