package com.coderwise.libs.settings

import kotlinx.coroutines.flow.Flow

interface SettingsDataStore<T> {
    val data: Flow<T>
    suspend fun updateData(transform: suspend (T) -> T)
}
