package com.coderwise.libs.sample.ui

import androidx.compose.runtime.Composable
import com.coderwise.libs.database.DatabaseDriverFactory
import com.coderwise.libs.location.LocationProvider
import com.coderwise.libs.settings.SettingsDataStoreFactory

/**
 * The few things a library cannot build for itself, because what they need differs by platform:
 * Android's factories take a `Context`, desktop's take the directory the app keeps its files in,
 * and the web and iOS ones take nothing. An app resolves these once in its DI graph — the
 * libraries ship Koin modules for exactly that — but the sample has no graph, so each target says
 * here, in a few lines, what it hands over.
 *
 * Read the actual for your target to see what your own app has to supply.
 */
@Composable
internal expect fun rememberSettingsDataStoreFactory(): SettingsDataStoreFactory

@Composable
internal expect fun rememberDatabaseDriverFactory(): DatabaseDriverFactory

/** Android needs a `Context`, iOS a CoreLocation delegate, and the browser its own geolocation. */
@Composable
internal expect fun rememberLocationProvider(): LocationProvider
