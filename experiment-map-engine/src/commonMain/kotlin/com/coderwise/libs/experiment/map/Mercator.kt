package com.coderwise.libs.experiment.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

data class LatLon(val lat: Double, val lon: Double)

private val Double.radians get() = this * PI / 180.0
private val Double.degrees get() = this * 180.0 / PI

/** Web Mercator, normalised to the unit square: x and y in `[0, 1)`, origin top-left. */
internal object Mercator {
    /** A tile is 256 *dp* on screen, so the map is the same physical size on every device. */
    const val WORLD_TILE = 256
    const val MAX_LAT = 85.05112877980659

    fun x(lon: Double) = ((lon + 180.0).mod(360.0)) / 360.0
    fun y(lat: Double) = 0.5 - ln(tan(PI / 4 + lat.coerceIn(-MAX_LAT, MAX_LAT).radians / 2)) / (2 * PI)
    fun lon(x: Double) = x.mod(1.0) * 360.0 - 180.0
    fun lat(y: Double) = atan(sinh(PI * (1 - 2 * y.coerceIn(0.0, 1.0)))).degrees
    fun worldPixels(zoom: Float, density: Float) = WORLD_TILE * density * 2.0.pow(zoom.toDouble())
}
