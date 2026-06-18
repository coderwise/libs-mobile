package com.coderwise.libs.settings

import android.content.Context
import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path.Companion.toPath

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsDataStoreFactory(private val context: Context) {
    actual fun <T> create(
        fileName: String,
        defaultValue: T,
        serializer: KSerializer<T>
    ): SettingsDataStore<T> {
        val path = context.filesDir.resolve(fileName).absolutePath.toPath()
        return createSettingsDataStore(
            fileSystem = FileSystem.SYSTEM,
            path = path,
            defaultValue = defaultValue,
            serializer = serializer
        )
    }
}
