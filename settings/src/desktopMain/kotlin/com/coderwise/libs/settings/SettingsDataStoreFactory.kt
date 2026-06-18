package com.coderwise.libs.settings

import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path.Companion.toPath

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsDataStoreFactory {
    actual fun <T> create(
        fileName: String,
        defaultValue: T,
        serializer: KSerializer<T>
    ): SettingsDataStore<T> {
        val path = (System.getProperty("user.home") + "/.mapsOn/$fileName").toPath()
        return createSettingsDataStore(
            fileSystem = FileSystem.SYSTEM,
            path = path,
            defaultValue = defaultValue,
            serializer = serializer
        )
    }
}
