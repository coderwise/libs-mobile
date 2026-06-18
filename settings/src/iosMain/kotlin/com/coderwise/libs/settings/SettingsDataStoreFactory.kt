package com.coderwise.libs.settings

import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlinx.cinterop.ExperimentalForeignApi

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsDataStoreFactory {
    @OptIn(ExperimentalForeignApi::class)
    actual fun <T> create(
        fileName: String,
        defaultValue: T,
        serializer: KSerializer<T>
    ): SettingsDataStore<T> {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        )
        val path = (documentDirectory?.path ?: "").toPath().resolve(fileName)
        return createSettingsDataStore(
            fileSystem = FileSystem.SYSTEM,
            path = path,
            defaultValue = defaultValue,
            serializer = serializer
        )
    }
}
