package com.coderwise.libs.locale

import kotlinx.browser.window

actual fun deviceRegionCode(): String? =
    regionCodeFromLanguageTag(window.navigator.language)
