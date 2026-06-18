package com.coderwise.libs.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {

    override suspend fun getCurrentLocation(): Result<GpsLocation> {
        return suspendCancellableCoroutine { cont ->
            val manager = CLLocationManager()
            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>
                ) {
                    manager.stopUpdatingLocation()
                    val loc = didUpdateLocations.lastOrNull() as? CLLocation
                    if (!cont.isActive) return
                    if (loc != null) {
                        cont.resume(Result.success(loc.toGpsLocation()))
                    } else {
                        cont.resume(Result.failure(IllegalStateException("No location received")))
                    }
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: NSError
                ) {
                    manager.stopUpdatingLocation()
                    if (cont.isActive) {
                        cont.resume(
                            Result.failure(
                                RuntimeException(didFailWithError.localizedDescription)
                            )
                        )
                    }
                }
            }
            manager.delegate = delegate
            manager.requestWhenInUseAuthorization()
            manager.startUpdatingLocation()
            cont.invokeOnCancellation {
                manager.stopUpdatingLocation()
                manager.delegate = null
            }
        }
    }

    override fun locationUpdates(): Flow<Result<GpsLocation>> = callbackFlow {
        val manager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(
                manager: CLLocationManager,
                didUpdateLocations: List<*>
            ) {
                val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                trySend(Result.success(loc.toGpsLocation()))
            }

            override fun locationManager(
                manager: CLLocationManager,
                didFailWithError: NSError
            ) {
                trySend(
                    Result.failure(
                        RuntimeException(didFailWithError.localizedDescription)
                    )
                )
            }
        }
        manager.delegate = delegate
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toGpsLocation(): GpsLocation {
    val (lat, lon) = coordinate.useContents { latitude to longitude }
    return GpsLocation(
        latitude = lat,
        longitude = lon,
        bearing = if (course >= 0) course.toFloat() else null,
        elevation = if (verticalAccuracy >= 0) altitude else null,
        time = (timestamp.timeIntervalSince1970 * 1000).toLong(),
        accuracy = if (horizontalAccuracy >= 0) horizontalAccuracy.toFloat() else null,
        speed = if (speed >= 0) speed.toFloat() else null
    )
}
