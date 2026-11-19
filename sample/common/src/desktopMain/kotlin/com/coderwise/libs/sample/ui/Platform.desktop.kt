package com.coderwise.libs.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coderwise.libs.database.DatabaseDriverFactory
import com.coderwise.libs.location.DesktopLocationProvider
import com.coderwise.libs.location.LocationProvider
import com.coderwise.libs.settings.SettingsDataStoreFactory
import kotlinx.io.files.Path

// Where an app keeps its files is the app's business, not the library's -- every desktop app that
// depends on it has a directory of its own -- so it is handed in rather than guessed.
private val dataDir = Path(System.getProperty("user.home"), ".coderwise-libraries-sample")

@Composable
internal actual fun rememberSettingsDataStoreFactory(): SettingsDataStoreFactory =
    remember { SettingsDataStoreFactory(dataDir) }

@Composable
internal actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory =
    remember { DatabaseDriverFactory(dataDir) }

@Composable
internal actual fun rememberLocationProvider(): LocationProvider = remember { DesktopLocationProvider() }
