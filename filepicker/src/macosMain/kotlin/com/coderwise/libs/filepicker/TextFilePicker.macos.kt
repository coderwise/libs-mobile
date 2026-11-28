package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberTextFilePicker(
    extensions: List<String>,
    title: String?,
    onPicked: (String?) -> Unit
): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return remember(extensions, title) {
        {
            val text = chooseMacPath(title, directories = false, extensions = extensions)?.let {
                NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, error = null)
            }
            callback.value(text)
        }
    }
}
