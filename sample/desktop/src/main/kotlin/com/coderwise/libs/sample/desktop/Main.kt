package com.coderwise.libs.sample.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.coderwise.libs.sample.ui.SampleApp
import java.awt.Taskbar
import javax.imageio.ImageIO

fun main() = application {
    val image = checkNotNull(Thread.currentThread().contextClassLoader.getResource("icon.png")) {
        "icon.png is missing from the desktop resources"
    }.let(ImageIO::read)

    // Window(icon = ...) covers the title bar and the Windows/Linux taskbar. The macOS Dock icon
    // belongs to the process rather than the window, so a run from Gradle -- as opposed to a
    // packaged app, which carries icon.icns -- keeps Java's default until it is set here.
    if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
        Taskbar.getTaskbar().iconImage = image
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Coderwise Libraries",
        icon = BitmapPainter(image.toComposeImageBitmap())
    ) {
        SampleApp()
    }
}
