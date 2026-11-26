package com.coderwise.libs.desktop

import androidx.compose.ui.unit.DpSize
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json

/** Remembers the main window's frame and zoomed state between runs, in one small JSON file. */
internal class WindowSettings(private val file: Path, val defaultSize: DpSize) {
    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    var prefs: WindowPrefs = WindowPrefs()
        private set

    /** A missing or unreadable file means defaults. */
    fun load(): WindowPrefs {
        prefs = runCatching {
            json.decodeFromString<WindowPrefs>(SystemFileSystem.source(file).buffered().use { it.readString() })
        }.getOrDefault(WindowPrefs())
        return prefs
    }

    fun save(updated: WindowPrefs) {
        if (updated == prefs) return
        prefs = updated
        runCatching {
            SystemFileSystem.sink(file).buffered().use { it.writeString(json.encodeToString(WindowPrefs.serializer(), updated)) }
        }
    }
}
