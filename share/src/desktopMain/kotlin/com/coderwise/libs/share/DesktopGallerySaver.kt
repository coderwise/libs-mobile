package com.coderwise.libs.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Saves a picture to the place the user picks in the platform's native Save dialog. */
class DesktopGallerySaver : GallerySaver {

    override suspend fun saveToGallery(file: ShareFile) {
        // AWT (and beneath it AppKit) requires dialogs to be created and shown on the event dispatch
        // thread. Opening one from a background thread leaves the native dialog unresponsive on macOS.
        val destination = withContext(Dispatchers.Swing) {
            val dialog = FileDialog(null as Frame?, "Save photo", FileDialog.SAVE).apply {
                directory = defaultDirectory().absolutePath
                setFile(file.name + extensionOf(file.mimeType))
                isVisible = true
            }
            val directory = dialog.directory
            val name = dialog.file
            dialog.dispose()
            // The dialog leaves both null when the user cancels; there is nothing to save or report.
            if (directory == null || name.isNullOrBlank()) null else File(directory, name)
        } ?: return
        withContext(Dispatchers.IO) { destination.writeBytes(file.bytes) }
    }
}

private fun defaultDirectory(): File {
    val home = File(System.getProperty("user.home"))
    val pictures = File(home, "Pictures")
    return if (pictures.isDirectory) pictures else home
}

private fun extensionOf(mimeType: String): String = when (mimeType.lowercase()) {
    "image/jpeg" -> ".jpg"
    "image/png" -> ".png"
    "image/gif" -> ".gif"
    "image/webp" -> ".webp"
    "image/bmp" -> ".bmp"
    "image/heic", "image/heif" -> ".heic"
    else -> ".bin"
}
