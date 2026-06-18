package com.coderwise.libs.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path

class DataStoreSettingsDataStore<T>(
    private val dataStore: DataStore<T>
) : SettingsDataStore<T> {
    override val data: Flow<T> = dataStore.data

    override suspend fun updateData(transform: suspend (T) -> T) {
        dataStore.updateData(transform)
    }
}

// Tolerate unknown keys so removing a settings field doesn't reset a user's
// persisted settings to defaults on upgrade.
private val settingsJson = Json { ignoreUnknownKeys = true }

internal class SettingsSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>
) : OkioSerializer<T> {
    override suspend fun readFrom(source: BufferedSource): T {
        val text = source.readUtf8()
        if (text.isEmpty()) return defaultValue
        return try {
            settingsJson.decodeFromString(serializer, text)
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        sink.writeUtf8(settingsJson.encodeToString(serializer, t))
    }
}

fun <T> createSettingsDataStore(
    fileSystem: FileSystem,
    path: Path,
    defaultValue: T,
    serializer: KSerializer<T>
): SettingsDataStore<T> {
    val dataStore = DataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = fileSystem,
            serializer = SettingsSerializer(defaultValue, serializer),
            producePath = { path }
        )
    )
    return DataStoreSettingsDataStore(dataStore)
}
