package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberFolderPicker(title: String?, onPicked: (String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return remember(title) { { callback.value(chooseMacPath(title, directories = true)) } }
}

actual val isFolderPickerSupported: Boolean = true
