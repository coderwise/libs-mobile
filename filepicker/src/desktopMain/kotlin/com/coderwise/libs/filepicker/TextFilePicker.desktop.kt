package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

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

/** Swing owns its dialogs from the event thread; reading the file afterwards does not. */
private fun chooseFile(extensions: List<String>, title: String?): File? {
    var chosen: File? = null
    EventQueue.invokeAndWait {
        val chooser = JFileChooser().apply {
            title?.let { dialogTitle = it }
            if (extensions.isNotEmpty()) {
                fileFilter = FileNameExtensionFilter(
                    extensions.joinToString(", ") { ".$it" },
                    *extensions.toTypedArray()
                )
            }
            isAcceptAllFileFilterUsed = true
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFile
    }
    return chosen
}
