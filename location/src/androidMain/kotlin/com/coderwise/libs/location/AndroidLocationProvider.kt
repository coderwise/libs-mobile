package com.coderwise.libs.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.location.Location as AndroidLocation

class AndroidLocationProvider(
    private val context: Context
) : LocationProvider {

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Result<GpsLocation> {
        if (!hasPermission()) {
            return Result.failure(
                SecurityException("Location permission not granted")
            )
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return Result.failure(IllegalStateException("LocationManager unavailable"))
        val provider = pickProvider(manager)
            ?: return Result.failure(IllegalStateException("No enabled location provider"))

        val android = suspendCancellableCoroutine<AndroidLocation?> { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context)
                ) { loc -> cont.resume(loc) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: AndroidLocation) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                    @Deprecated("Deprecated in API 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

        return android?.let {
            Result.success(it.toGpsLocation())
        } ?: Result.failure(IllegalStateException("Location unavailable"))
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(): Flow<Result<GpsLocation>> {
        if (!hasPermission()) {
            return flowOf(
                Result.failure(
                    SecurityException("Location permission not granted")
                )
            )
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return flowOf(Result.failure(IllegalStateException("LocationManager unavailable")))
        val provider = pickProvider(manager)
            ?: return flowOf(Result.failure(IllegalStateException("No enabled location provider")))

        return callbackFlow {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: AndroidLocation) {
                    trySend(Result.success(location.toGpsLocation()))
                }

                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Deprecated("Deprecated in API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            manager.requestLocationUpdates(
                provider,
                MIN_UPDATE_INTERVAL_MS,
                MIN_UPDATE_DISTANCE_M,
                listener,
                Looper.getMainLooper()
            )
            awaitClose { manager.removeUpdates(listener) }
        }
    }

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun pickProvider(manager: LocationManager): String? {
        val providers = manager.getProviders(true)
        return when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> providers.firstOrNull()
        }
    }

    private companion object {
        const val MIN_UPDATE_INTERVAL_MS = 1000L
        const val MIN_UPDATE_DISTANCE_M = 1f
    }
}

private fun AndroidLocation.toGpsLocation() = GpsLocation(
    latitude = latitude,
    longitude = longitude,
    bearing = if (hasBearing()) bearing else null,
    elevation = if (hasAltitude()) altitude else null,
    time = time,
    accuracy = if (hasAccuracy()) accuracy else null,
    speed = if (hasSpeed()) speed else null
)
