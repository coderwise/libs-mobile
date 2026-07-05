package com.coderwise.libs.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point for the iOS app — called from Swift (sample/ios). */
fun MainViewController(): UIViewController = ComposeUIViewController { SampleApp() }
