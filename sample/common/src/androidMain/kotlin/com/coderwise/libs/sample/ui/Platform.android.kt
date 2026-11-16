package com.coderwise.libs.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.coderwise.libs.database.DatabaseDriverFactory
import com.coderwise.libs.location.AndroidLocationProvider
import com.coderwise.libs.location.LocationProvider
import com.coderwise.libs.settings.SettingsDataStoreFactory

// The application context, not the activity's: these outlive any one screen, and a factory that
// held an activity would keep it alive.

@Composable
internal actual fun rememberSettingsDataStoreFactory(): SettingsDataStoreFactory {
    val context = LocalContext.current.applicationContext
    return remember(context) { SettingsDataStoreFactory(context) }
}

@Composable
internal actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = LocalContext.current.applicationContext
    return remember(context) { DatabaseDriverFactory(context) }
}

@Composable
internal actual fun rememberLocationProvider(): LocationProvider {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidLocationProvider(context) }
}
