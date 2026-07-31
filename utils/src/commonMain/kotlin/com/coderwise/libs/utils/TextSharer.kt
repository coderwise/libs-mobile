package com.coderwise.libs.utils

/**
 * Shares plain text via the platform's native share/clipboard mechanism.
 *
 * For callers outside composition (view models, services). Compose code should
 * use [rememberShareTextLauncher], which resolves the Android context from the
 * composition rather than from Koin's global registry.
 */
expect fun shareText(text: String)
