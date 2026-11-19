package com.coderwise.libs.locale

import android.os.LocaleList

actual fun deviceRegionCode(): String? =
    LocaleList.getDefault()[0].country.takeIf { it.length == 2 }?.uppercase()
