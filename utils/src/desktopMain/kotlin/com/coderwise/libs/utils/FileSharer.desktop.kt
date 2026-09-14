package com.coderwise.libs.utils

import java.awt.Desktop
import java.io.File

actual fun shareFile(fileName: String, mimeType: String, writeContent: (Appendable) -> Unit) {
    val downloads = File(System.getProperty("user.home"), "Downloads")
    File(downloads, fileName).bufferedWriter().use { writeContent(it) }
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(downloads)
    }
}
