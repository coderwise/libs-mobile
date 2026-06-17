package com.coderwise.libs.utils

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun shareText(text: String) {
    val activityVC = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null
    )
    @Suppress("DEPRECATION")
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(activityVC, animated = true, completion = null)
}
