package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable

@Composable
actual fun rememberTextSourcePicker(
    extensions: List<String>,
    title: String?,
    onPicked: (FilePick) -> Unit
): () -> Unit = rememberWholeFileTextSourcePicker(extensions, title, onPicked)
