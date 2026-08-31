package com.coderwise.libs.location

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The same Geolocation API the js target uses, reached without `dynamic`: each fix crosses the
 * boundary as plain numbers rather than as an object to read fields off, and the fields the
 * browser leaves empty (altitude, heading, speed) arrive as NaN — which is also how the js
 * implementation ends up treating them.
 */
class WasmJsLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): Result<GpsLocation> =
        suspendCancellableCoroutine { continuation ->
            currentPosition(
                onSuccess = { latitude, longitude, accuracy, timestamp, altitude, heading, speed ->
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.success(
                                gpsLocation(latitude, longitude, accuracy, timestamp, altitude, heading, speed)
                            )
                        )
                    }
                },
                onError = { message ->
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(RuntimeException(message)))
                    }
                }
            )
        }

    override fun locationUpdates(): Flow<Result<GpsLocation>> {
        if (!hasGeolocation()) {
            return flowOf(Result.failure(IllegalStateException("Geolocation unavailable")))
        }
        return callbackFlow {
            val watchId = watchPosition(
                onSuccess = { latitude, longitude, accuracy, timestamp, altitude, heading, speed ->
                    trySend(
                        Result.success(
                            gpsLocation(latitude, longitude, accuracy, timestamp, altitude, heading, speed)
                        )
                    )
                },
                onError = { message -> trySend(Result.failure(RuntimeException(message))) }
            )
            awaitClose { clearWatch(watchId) }
        }
    }
}

private fun gpsLocation(
    latitude: Double,
    longitude: Double,
    accuracy: Double,
    timestamp: Double,
    altitude: Double,
    heading: Double,
    speed: Double
) = GpsLocation(
    latitude = latitude,
    longitude = longitude,
    bearing = heading.toFloat().takeIf { !it.isNaN() },
    elevation = altitude.takeIf { !it.isNaN() },
    time = timestamp.toLong(),
    accuracy = accuracy.toFloat(),
    speed = speed.toFloat().takeIf { !it.isNaN() }
)

private fun hasGeolocation(): Boolean = js("!!navigator.geolocation")

private fun currentPosition(
    onSuccess: (
        latitude: Double,
        longitude: Double,
        accuracy: Double,
        timestamp: Double,
        altitude: Double,
        heading: Double,
        speed: Double
    ) -> Unit,
    onError: (message: String) -> Unit
): Unit = js(
    """{
        if (!navigator.geolocation) { onError('Geolocation unavailable'); return; }
        navigator.geolocation.getCurrentPosition(
            (position) => {
                const coords = position.coords;
                onSuccess(
                    coords.latitude, coords.longitude, coords.accuracy, position.timestamp,
                    coords.altitude === null ? NaN : coords.altitude,
                    coords.heading === null ? NaN : coords.heading,
                    coords.speed === null ? NaN : coords.speed
                );
            },
            (error) => onError(error.message || 'Geolocation error')
        );
    }"""
)

private fun watchPosition(
    onSuccess: (
        latitude: Double,
        longitude: Double,
        accuracy: Double,
        timestamp: Double,
        altitude: Double,
        heading: Double,
        speed: Double
    ) -> Unit,
    onError: (message: String) -> Unit
): Int = js(
    """{
        return navigator.geolocation.watchPosition(
            (position) => {
                const coords = position.coords;
                onSuccess(
                    coords.latitude, coords.longitude, coords.accuracy, position.timestamp,
                    coords.altitude === null ? NaN : coords.altitude,
                    coords.heading === null ? NaN : coords.heading,
                    coords.speed === null ? NaN : coords.speed
                );
            },
            (error) => onError(error.message || 'Geolocation error')
        );
    }"""
)

private fun clearWatch(watchId: Int): Unit = js("navigator.geolocation.clearWatch(watchId)")
