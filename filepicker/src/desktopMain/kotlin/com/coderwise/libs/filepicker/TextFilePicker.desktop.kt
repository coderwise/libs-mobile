package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberTextFilePicker(
    extensions: List<String>,
    title: String?,
    onPicked: (String?) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    return remember(scope, extensions, title) {
        {
            scope.launch {
                val file = withContext(Dispatchers.IO) { chooseFile(extensions, title) }
                callback.value(file?.let { runCatching { it.readText() }.getOrNull() })
            }
        }
    }
}

/**
 * AWT's [FileDialog] rather than Swing's `JFileChooser`: it is the desktop's own open panel —
 * Finder's sidebar, its search, the places the user actually keeps files — where the Swing chooser
 * is a Java-looking window that belongs to no platform. Same reason `:imagepicker` uses it.
 *
 * Swing owns its dialogs from the event thread; reading the file afterwards does not.
 */
private fun chooseFile(extensions: List<String>, title: String?): File? {
    var chosen: File? = null
    EventQueue.invokeAndWait {
        val dialog = FileDialog(null as Frame?, title ?: "Open", FileDialog.LOAD).apply {
            if (extensions.isNotEmpty()) {
                val allowed = extensions.map { it.removePrefix(".").lowercase() }.toSet()
                // Honoured on macOS/Linux; Windows AWT ignores the filter, so it also gets the
                // wildcard below, which is the only extension filter that dialog understands.
                setFilenameFilter { _, name ->
                    name.substringAfterLast('.', "").lowercase() in allowed
                }
                file = allowed.joinToString(";") { "*.$it" }
            }
            isVisible = true
        }
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) chosen = File(dir, name)
    }
    return chosen
}
