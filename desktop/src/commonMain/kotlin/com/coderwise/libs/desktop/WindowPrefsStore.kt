package com.coderwise.libs.desktop

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json

/** Reads and writes the remembered [WindowPrefs] as one small JSON file; each platform maps them to its own window. */
internal class WindowPrefsStore(private val file: Path) {
    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    /** The last loaded or saved prefs. */
    var prefs: WindowPrefs = WindowPrefs()
        private set

    /** A missing or unreadable file means defaults. */
    fun load(): WindowPrefs {
        prefs = runCatching {
            json.decodeFromString<WindowPrefs>(SystemFileSystem.source(file).buffered().use { it.readString() })
        }.getOrDefault(WindowPrefs())
        return prefs
    }

    /** Writes only when something changed; a failed write is ignored. */
    fun save(updated: WindowPrefs) {
        if (updated == prefs) return
        prefs = updated
        runCatching {
            file.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(file).buffered().use { it.writeString(json.encodeToString(WindowPrefs.serializer(), updated)) }
        }
    }
}
