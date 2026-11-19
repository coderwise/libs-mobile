package com.coderwise.libs.locale

// The JVM sets user.country from the OS locale at startup; it is the same value Locale.getDefault()
// starts with, read without the Java locale type.
actual fun deviceRegionCode(): String? =
    System.getProperty("user.country")?.takeIf { it.length == 2 }?.uppercase()
