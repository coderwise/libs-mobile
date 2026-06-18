@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.coderwise.libs.settings

import kotlinx.serialization.KSerializer

expect class SettingsDataStoreFactory {
    fun <T> create(
        fileName: String,
        defaultValue: T,
        serializer: KSerializer<T>
    ): SettingsDataStore<T>
}
