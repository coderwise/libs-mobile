package com.coderwise.libs.sample.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.coderwise.libs.sample.ui.SampleApp

fun main() {
    // AWT's own windows -- the native file dialog among them -- render in the light appearance
    // unless told to follow the system, and the property is read when the toolkit starts, so it
    // has to be set before anything opens a window.
    System.setProperty("apple.awt.application.appearance", "system")
    application {
        Window(onCloseRequest = ::exitApplication, title = "Coderwise Libraries") {
            SampleApp()
        }
    }
}
