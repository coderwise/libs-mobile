package com.coderwise.libs.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coderwise.libs.database.DatabaseDriverFactory
import com.coderwise.libs.location.LocationProvider
import com.coderwise.libs.location.WasmJsLocationProvider
import com.coderwise.libs.settings.SettingsDataStoreFactory

@Composable
internal actual fun rememberSettingsDataStoreFactory(): SettingsDataStoreFactory =
    remember { SettingsDataStoreFactory() }

@Composable
internal actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory =
    remember { DatabaseDriverFactory() }

@Composable
internal actual fun rememberLocationProvider(): LocationProvider = remember { WasmJsLocationProvider() }
