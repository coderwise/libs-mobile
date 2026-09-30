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

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

@Composable
actual fun rememberPhotoPicker(
    maxDimensionPx: Int?,
    maxItems: Int?,
    onResult: (List<PickedImage>) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    return {
        scope.launch {
            val images = withContext(Dispatchers.IO) {
                val dialog = FileDialog(null as Frame?, "Choose image", FileDialog.LOAD).apply {
                    isMultipleMode = maxItems != 1
                    // Honoured on macOS/Linux; Windows AWT ignores the filter but still opens the dialog.
                    setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                    isVisible = true
                }
                dialog.files.take(maxItems ?: Int.MAX_VALUE).mapNotNull { file ->
                    runCatching { pickedImage(file.readBytes(), maxDimensionPx) }.getOrNull()
                }
            }
            currentOnResult(images)
        }
    }
}
