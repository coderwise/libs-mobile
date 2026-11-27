package com.coderwise.libs.share

import java.awt.Desktop
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import java.io.File

actual fun shareFile(fileName: String, mimeType: String, writeContent: (Appendable) -> Unit) {
    val downloads = Path(System.getProperty("user.home"), "Downloads")
    SystemFileSystem.createDirectories(downloads)
    SystemFileSystem.sink(Path(downloads, fileName)).buffered().use { sink ->
        writeContent(object : Appendable {
            override fun append(csq: CharSequence?) = apply { sink.writeString(csq.toString()) }
            override fun append(csq: CharSequence?, start: Int, end: Int) = apply { sink.writeString(csq.toString().substring(start, end)) }
            override fun append(c: Char) = apply { sink.writeString(c.toString()) }
        })
    }
    // java.awt.Desktop only takes a java.io.File: the one place a File has to exist.
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(File(downloads.toString()))
    }
}
