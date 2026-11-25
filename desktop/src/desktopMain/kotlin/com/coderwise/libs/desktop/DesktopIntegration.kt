package com.coderwise.libs.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Desktop
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

internal fun loadIcon(resourcePath: String): BufferedImage? =
    runCatching {
        Thread.currentThread().contextClassLoader.getResource(resourcePath.removePrefix("/"))?.let { ImageIO.read(it) }
    }.getOrNull()

internal fun BufferedImage.toPainter(): Painter = BitmapPainter(toComposeImageBitmap())

internal fun setDockIcon(image: BufferedImage?) {
    if (image == null) return
    runCatching {
        if (Taskbar.isTaskbarSupported()) {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.setIconImage(image)
        }
    }
}

internal fun setAboutHandler(onAbout: () -> Unit) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler { SwingUtilities.invokeLater(onAbout) }
            }
        }
    }
}
