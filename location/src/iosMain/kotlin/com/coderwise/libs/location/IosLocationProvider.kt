package com.coderwise.libs.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLActivityType
import platform.CoreLocation.CLActivityTypeOther
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * [LocationProvider] over CoreLocation.
 *
 * Foreground-shaped by default, which is what a map screen or a one-off "where
 * am I" wants: When-In-Use authorization and no background flags, so the app
 * needs nothing beyond `NSLocationWhenInUseUsageDescription`.
 *
 * Continuous tracking that has to outlive the app being backgrounded — trip
 * recording, a route log — is opt-in via [backgroundUpdates], because it is not
 * free: it requires `location` in the consumer's `UIBackgroundModes`, and
 * enabling it *without* that key makes CoreLocation throw. Defaulting it off
 * keeps that cost with the apps that ask for it.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider(
    /**
     * Keep [locationUpdates] delivering while the app is backgrounded. Requires
     * `location` in the consumer app's `UIBackgroundModes` — CoreLocation throws
     * at runtime otherwise. Also stops iOS from auto-pausing updates when it
     * decides the device has stopped moving (it only resumes via a delegate
     * callback, so a pause would otherwise end the stream for good), and asks to
     * escalate authorization to Always.
     */
    private val backgroundUpdates: Boolean = false,
    /**
     * How iOS should tune its filtering and pause heuristics — e.g.
     * `CLActivityTypeAutomotiveNavigation` for a car, `CLActivityTypeFitness`
     * for on-foot tracking.
     */
    private val activityType: CLActivityType = CLActivityTypeOther,
) : LocationProvider {

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
        val manager = CLLocationManager().apply { configure() }
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
        manager.requestAuthorization()
        manager.startUpdatingLocation()
        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
        // CoreLocation delivers delegate callbacks on the run loop of the thread
        // that created the manager, and a coroutine worker thread has none — so
        // the manager is built on main, where there is always a live run loop,
        // rather than wherever the collector happens to run.
    }.flowOn(Dispatchers.Main)

    private fun CLLocationManager.configure() {
        activityType = this@IosLocationProvider.activityType
        if (!backgroundUpdates) return
        allowsBackgroundLocationUpdates = true
        pausesLocationUpdatesAutomatically = false
        // Mandatory under When-In-Use + background updates, and an honest
        // "still tracking" tell under Always too.
        showsBackgroundLocationIndicator = true
    }

    /**
     * Always can only be asked for once When-In-Use is held: from
     * not-determined, iOS shows the ordinary prompt and holds the Always
     * upgrade back for later. Asking in that order keeps the upgrade prompt
     * available instead of spending the one chance at it.
     */
    private fun CLLocationManager.requestAuthorization() {
        if (!backgroundUpdates) {
            requestWhenInUseAuthorization()
            return
        }
        when (authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> requestWhenInUseAuthorization()
            kCLAuthorizationStatusAuthorizedWhenInUse -> requestAlwaysAuthorization()
            else -> Unit // already Always, or refused — prompting again shows nothing
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
