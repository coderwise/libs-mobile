package com.coderwise.libs.locale

import java.util.Locale

actual fun deviceRegionCode(): String? =
    Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase()
