package com.coderwise.libs.utils.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        val application = UIApplication.sharedApplication
        application.idleTimerDisabled = true
        // iOS clears idleTimerDisabled whenever the app leaves the foreground, and the composition
        // survives that, so re-arm the flag every time the app becomes active again.
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            application.idleTimerDisabled = true
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            application.idleTimerDisabled = false
        }
    }
}
