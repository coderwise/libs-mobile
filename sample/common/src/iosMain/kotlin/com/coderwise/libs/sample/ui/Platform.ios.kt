package com.coderwise.libs.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coderwise.libs.database.DatabaseDriverFactory
import com.coderwise.libs.location.IosLocationProvider
import com.coderwise.libs.location.LocationProvider
import com.coderwise.libs.settings.SettingsDataStoreFactory

@Composable
internal actual fun rememberSettingsDataStoreFactory(): SettingsDataStoreFactory =
    remember { SettingsDataStoreFactory() }

@Composable
internal actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory =
    remember { DatabaseDriverFactory() }

// The defaults: updates stop when the app is backgrounded. Background delivery is opt-in, and
// needs `location` in the app's UIBackgroundModes -- CoreLocation throws without it.
@Composable
internal actual fun rememberLocationProvider(): LocationProvider = remember { IosLocationProvider() }
