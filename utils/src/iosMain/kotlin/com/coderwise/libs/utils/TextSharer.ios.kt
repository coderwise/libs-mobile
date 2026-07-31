package com.coderwise.libs.utils

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

actual fun shareText(text: String) = presentShareSheet(text)

internal fun presentShareSheet(text: String) {
    val presenter = topViewController() ?: return
    val controller = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null
    )
    // On iPad the activity controller is a popover and crashes without an anchor.
    controller.popoverPresentationController?.sourceView = presenter.view
    presenter.presentViewController(controller, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) {
        top = top.presentedViewController ?: return top
    }
}
