package com.coderwise.libs.location

import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    suspend fun getCurrentLocation(): Result<GpsLocation>

    fun locationUpdates(): Flow<Result<GpsLocation>>
}
