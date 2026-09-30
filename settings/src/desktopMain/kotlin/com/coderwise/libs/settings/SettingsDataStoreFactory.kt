package com.coderwise.libs.settings

import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Settings files go in [directory], which the app names. It used to be `~/.mapsOn` for every app,
 * so each desktop app that used this library read and overwrote the others' settings.
 *
 * Where that directory is, is the application's business and not this library's — see
 * `SETTINGS_DIR_QUALIFIER`, under which `platformSettingsModule` asks the app graph for it.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsDataStoreFactory(private val directory: File) {
    actual fun <T> create(
        fileName: String,
        defaultValue: T,
        serializer: KSerializer<T>
    ): SettingsDataStore<T> {
        directory.mkdirs()
        return createSettingsDataStore(
            fileSystem = FileSystem.SYSTEM,
            path = File(directory, fileName).toOkioPath(),
            defaultValue = defaultValue,
            serializer = serializer
        )
    }
}
