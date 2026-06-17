package com.coderwise.libs.utils

import java.awt.Desktop
import java.io.File

actual fun shareFile(fileName: String, content: String, mimeType: String) {
    val downloads = File(System.getProperty("user.home"), "Downloads")
    val target = File(downloads, fileName).also { it.writeText(content) }
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(downloads)
    }
}
