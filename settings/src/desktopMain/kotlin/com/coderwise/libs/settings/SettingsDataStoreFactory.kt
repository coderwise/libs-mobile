package com.coderwise.libs.settings

import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Settings files go in [directory] (a [File] works too), which the app names. It used to be `~/.mapsOn` for every app,
 * so each desktop app that used this library read and overwrote the others' settings.
 *
 * Where that directory is, is the application's business and not this library's — see
 * `SETTINGS_DIR_QUALIFIER`, under which `platformSettingsModule` asks the app graph for it.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsDataStoreFactory(private val directory: Path) {
    constructor(directory: File) : this(Path(directory.path))

    actual fun <T> create(
        fileName: String,
        defaultValue: T,
        serializer: KSerializer<T>
    ): SettingsDataStore<T> {
        SystemFileSystem.createDirectories(directory)
        return createSettingsDataStore(
            fileSystem = FileSystem.SYSTEM,
            path = Path(directory, fileName).toString().toPath(),
            defaultValue = defaultValue,
            serializer = serializer
        )
    }
}
