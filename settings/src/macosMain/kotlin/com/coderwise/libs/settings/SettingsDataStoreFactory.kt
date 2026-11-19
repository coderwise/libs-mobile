package com.coderwise.libs.settings

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Settings files go in [directory], which the app names — see `SETTINGS_DIR_QUALIFIER`, under which
 * `platformSettingsModule` asks the app graph for it.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsDataStoreFactory(private val directory: Path) {
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
