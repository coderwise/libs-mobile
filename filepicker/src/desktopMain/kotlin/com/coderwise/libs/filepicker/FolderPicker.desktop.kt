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
import javax.swing.JFileChooser

@Composable
actual fun rememberFolderPicker(title: String?, onPicked: (String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    return remember(scope, title) {
        { scope.launch { callback.value(withContext(Dispatchers.IO) { chooseFolder(title) }) } }
    }
}

actual val isFolderPickerSupported: Boolean = true

private val isMac = System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)

/**
 * On macOS, AWT's [FileDialog] in its directory mode is the system open panel; elsewhere AWT has
 * no folder mode, so Swing's [JFileChooser] is the only option. Dialogs belong to the event thread.
 */
private fun chooseFolder(title: String?): String? {
    var chosen: String? = null
    EventQueue.invokeAndWait {
        chosen = if (isMac) {
            val key = "apple.awt.fileDialogForDirectories"
            System.setProperty(key, "true")
            try {
                val dialog = FileDialog(null as Frame?, title ?: "Choose folder", FileDialog.LOAD).apply { isVisible = true }
                val dir = dialog.directory
                val name = dialog.file
                if (dir != null && name != null) java.io.File(dir, name).path else null
            } finally {
                System.setProperty(key, "false")
            }
        } else {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                title?.let { dialogTitle = it }
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.path else null
        }
    }
    return chosen
}
