package com.coderwise.libs.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DesktopLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): Result<GpsLocation> =
        Result.failure(
            UnsupportedOperationException("Location not supported on desktop")
        )

    override fun locationUpdates(): Flow<Result<GpsLocation>> = flowOf(
        Result.failure(
            UnsupportedOperationException("Location not supported on desktop")
        )
    )
}
