package com.coderwise.libs.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coderwise.libs.database.DatabaseDriverFactory
import com.coderwise.libs.location.JsLocationProvider
import com.coderwise.libs.location.LocationProvider
import com.coderwise.libs.settings.SettingsDataStoreFactory

// The two web targets differ only in their location provider: geolocation is reached through
// `dynamic` on js and through js() functions on wasmJs.

@Composable
internal actual fun rememberSettingsDataStoreFactory(): SettingsDataStoreFactory =
    remember { SettingsDataStoreFactory() }

@Composable
internal actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory =
    remember { DatabaseDriverFactory() }

@Composable
internal actual fun rememberLocationProvider(): LocationProvider = remember { JsLocationProvider() }
