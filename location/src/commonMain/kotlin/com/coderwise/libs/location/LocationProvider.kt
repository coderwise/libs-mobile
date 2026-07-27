package com.coderwise.libs.location

import kotlinx.coroutines.flow.Flow

/**
 * GPS fixes, wherever the app happens to be running.
 *
 * Both calls fail rather than throw when access is refused — check and request the grant
 * first, e.g. with `rememberLocationPermissionState` from `com.coderwise.libs:permissions`.
 *
 * The host app declares what it uses: `android.permission.ACCESS_FINE_LOCATION` and/or
 * `ACCESS_COARSE_LOCATION` in its manifest, and NSLocationWhenInUseUsageDescription in
 * Info.plist. This library declares neither, so that apps which only register
 * `locationModule` without resolving this interface don't ship a permission they never ask for.
 */
interface LocationProvider {
    suspend fun getCurrentLocation(): Result<GpsLocation>

    fun locationUpdates(): Flow<Result<GpsLocation>>
}
