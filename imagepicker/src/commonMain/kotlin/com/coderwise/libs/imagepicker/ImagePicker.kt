package com.coderwise.libs.imagepicker

import androidx.compose.runtime.Composable

/**
 * Remembers a platform image picker. Returns a `launch` lambda to wire to a button's click;
 * invoking it opens the system photo/file picker. [onResult] is called with the chosen image's
 * raw bytes, or `null` if the user cancelled or the read failed.
 */
@Composable
expect fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit
