package com.coderwise.libs.imagepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    return {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val dialog = FileDialog(null as Frame?, "Choose image", FileDialog.LOAD).apply {
                    // Honoured on macOS/Linux; Windows AWT ignores the filter but still opens the dialog.
                    setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                    isVisible = true
                }
                val dir = dialog.directory
                val name = dialog.file
                if (dir != null && name != null) {
                    runCatching { downscaleImageBytes(File(dir, name).readBytes()) }.getOrNull()
                } else {
                    null
                }
            }
            currentOnResult(bytes)
        }
    }
}
