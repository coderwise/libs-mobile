package com.coderwise.libs.settings

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsDataStoreFactory {
    actual fun <T> create(
        fileName: String,
        defaultValue: T,
        serializer: KSerializer<T>
    ): SettingsDataStore<T> = LocalStorageSettingsDataStore(fileName, defaultValue, serializer)
}

// ignoreUnknownKeys mirrors the file-backed platforms so dropping a settings field
// doesn't reset persisted settings on upgrade.
private val settingsJson = Json { ignoreUnknownKeys = true }

/**
 * Browser-backed settings store keyed by [storageKey] in `localStorage`, so web
 * settings now survive a page reload instead of living only in memory. All
 * `localStorage` access is guarded so a non-browser JS host (e.g. Node during
 * tests/SSR) degrades to an in-memory store rather than throwing.
 */
private class LocalStorageSettingsDataStore<T>(
    private val storageKey: String,
    private val defaultValue: T,
    private val serializer: KSerializer<T>
) : SettingsDataStore<T> {

    private val state = MutableStateFlow(readPersisted())
    override val data: Flow<T> = state

    override suspend fun updateData(transform: suspend (T) -> T) {
        val next = transform(state.value)
        state.value = next
        runCatching { localStorage.setItem(storageKey, settingsJson.encodeToString(serializer, next)) }
    }

    private fun readPersisted(): T = runCatching {
        localStorage.getItem(storageKey)?.let { settingsJson.decodeFromString(serializer, it) }
    }.getOrNull() ?: defaultValue
}
