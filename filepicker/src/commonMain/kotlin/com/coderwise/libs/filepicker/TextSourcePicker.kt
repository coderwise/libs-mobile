package com.coderwise.libs.filepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * A text file read a piece at a time, so a document too big to hold as one string can still be
 * parsed. Not thread-safe; read it from one coroutine and [close] it when done.
 */
interface TextSource {
    /** The next piece of the file, or `null` once it has all been read. Throws if the read fails. */
    suspend fun readChunk(): String?

    /** Releases the file. Safe to call more than once. */
    fun close()
}

/** What the user did with the chooser opened by [rememberTextSourcePicker]. */
sealed interface FilePick {
    /** The chooser was dismissed without a file. */
    data object Cancelled : FilePick

    /** A file was chosen but could not be opened; [message] is the system's reason, if it gave one. */
    class Failed(val message: String?) : FilePick

    /** A file was chosen. The receiver owns [source] and must close it. */
    class Picked(val source: TextSource) : FilePick
}

/**
 * Like [rememberTextFilePicker], for documents that may be large, and telling a failed read apart
 * from a cancelled chooser — [rememberTextFilePicker] reports both as `null`, which leaves a caller
 * unable to say anything to a user whose file could not be read.
 *
 * Android streams the file. The other platforms already hold the whole file as a string by the
 * time they hand it over, so there they serve it in pieces rather than save any memory.
 */
@Composable
expect fun rememberTextSourcePicker(
    extensions: List<String> = emptyList(),
    title: String? = null,
    onPicked: (FilePick) -> Unit
): () -> Unit

/** A [TextSource] over a string already in memory. */
class StringTextSource(private val text: String, private val chunkSize: Int = CHUNK_CHARS) : TextSource {
    private var position = 0

    override suspend fun readChunk(): String? {
        if (position >= text.length) return null
        val end = minOf(position + chunkSize, text.length)
        return text.substring(position, end).also { position = end }
    }

    override fun close() {
        position = text.length
    }
}

/** The shared implementation for platforms that read the whole file up front. */
@Composable
internal fun rememberWholeFileTextSourcePicker(
    extensions: List<String>,
    title: String?,
    onPicked: (FilePick) -> Unit
): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val launch = rememberTextFilePicker(extensions, title) { text ->
        callback.value(if (text == null) FilePick.Cancelled else FilePick.Picked(StringTextSource(text)))
    }
    return remember(launch) { launch }
}

internal const val CHUNK_CHARS = 64 * 1024
