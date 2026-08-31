package com.coderwise.libs.filepicker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberTextFilePicker(
    extensions: List<String>,
    title: String?,
    onPicked: (String?) -> Unit
): () -> Unit {
    if (LocalInspectionMode.current) return {}
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            currentOnPicked(null)
        } else {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                    }.getOrNull()
                }
                currentOnPicked(text)
            }
        }
    }
    // Anything, rather than a type derived from [extensions]. A document arrives on Android with
    // whatever type the app that wrote it declared -- often application/octet-stream, or nothing at
    // all from a cloud provider -- and a filter that is right about the format greys out the very
    // file the user came to open. SAF has no extension filter to use instead.
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}
