package com.coderwise.libs.sample.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.coderwise.libs.sample.ui.SampleApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Map Sample") {
        SampleApp()
    }
}
