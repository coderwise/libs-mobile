package com.coderwise.libs.filepicker

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.Reader

@Composable
actual fun rememberTextSourcePicker(
    extensions: List<String>,
    title: String?,
    onPicked: (FilePick) -> Unit
): () -> Unit {
    if (LocalInspectionMode.current) return {}
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        currentOnPicked(if (uri == null) FilePick.Cancelled else context.openTextSource(uri))
    }
    // Anything, for the reason given on [rememberTextFilePicker]: SAF has no extension filter, and
    // a type guessed from [extensions] greys out files whose provider declared another one.
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}

/** Opening is quick and done here; the reading is what is left to the caller, a piece at a time. */
private fun Context.openTextSource(uri: Uri): FilePick = try {
    val stream = contentResolver.openInputStream(uri)
        ?: return FilePick.Failed("The file could not be opened")
    FilePick.Picked(ReaderTextSource(InputStreamReader(stream, Charsets.UTF_8)))
} catch (e: Exception) {
    FilePick.Failed(e.message)
}

private class ReaderTextSource(private val reader: Reader) : TextSource {
    private val buffer = CharArray(CHUNK_CHARS)

    override suspend fun readChunk(): String? = withContext(Dispatchers.IO) {
        val read = reader.read(buffer)
        if (read < 0) null else String(buffer, 0, read)
    }

    override fun close() {
        runCatching { reader.close() }
    }
}
