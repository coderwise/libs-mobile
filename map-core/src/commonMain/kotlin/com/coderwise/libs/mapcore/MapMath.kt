package com.coderwise.libs.mapcore

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Utility functions for map-related mathematical calculations,
 * specifically for converting between geographic coordinates (Latitude/Longitude)
 * and Web Mercator tile coordinates.
 */
object MapMath {
    /**
     * Converts latitude and longitude to integer tile coordinates at a given zoom level.
     *
     * @param latitude The latitude in degrees.
     * @param longitude The longitude in degrees.
     * @param zoom The zoom level.
     * @return A [Pair] containing the integer X and Y tile coordinates.
     */
    fun latLonToTile(latitude: Double, longitude: Double, zoom: Int): Pair<Int, Int> {
        val tileCount = 2.0.pow(zoom)
        val x = ((longitude + 180.0) / 360.0 * tileCount).toInt()
        val latitudeRadians = Math.toRadians(latitude)
        val y = ((1.0 - ln(tan(latitudeRadians) + 1.0 / cos(latitudeRadians)) / PI) / 2.0 * tileCount).toInt()
        return x to y
    }

    /**
     * Converts latitude and longitude to fractional tile coordinates at a given zoom level.
     * This provides more precision than [latLonToTile] and is useful for smooth panning or positioning.
     *
     * Example:
     * latitude: 51.5074, longitude: -0.1278, zoom: 10 -> x: 511.637, y: 340.505
     *
     * @param latitude The latitude in degrees.
     * @param longitude The longitude in degrees.
     * @param zoom The zoom level as a [Double] to support fractional zoom.
     * @return A [Pair] containing the fractional X and Y coordinates.
     */
    fun latLonToTileFractional(latitude: Double, longitude: Double, zoom: Double): Pair<Double, Double> {
        val tileCount = 2.0.pow(zoom)
        val x = (longitude + 180.0) / 360.0 * tileCount
        val latitudeRadians = Math.toRadians(latitude)
        val y = (1.0 - ln(tan(latitudeRadians) + 1.0 / cos(latitudeRadians)) / PI) / 2.0 * tileCount
        return x to y
    }

    /**
     * Converts fractional tile coordinates and zoom back to latitude and longitude.
     *
     * @param x The fractional X coordinate in the tile grid.
     * @param y The fractional Y coordinate in the tile grid.
     * @param zoom The zoom level as a [Double] to support fractional zoom.
     * @return A [Pair] containing the latitude and longitude in degrees.
     */
    fun tileToLatLon(x: Double, y: Double, zoom: Double): Pair<Double, Double> {
        val tileCount = 2.0.pow(zoom)
        val longitude = x / tileCount * 360.0 - 180.0
        val latitudeRadians = atan(0.5 * (exp(PI * (1.0 - 2.0 * y / tileCount)) - exp(-PI * (1.0 - 2.0 * y / tileCount))))
        val latitude = Math.toDegrees(latitudeRadians)
        return latitude to longitude
    }
}

private object Math {
    fun toRadians(degrees: Double): Double = degrees / 180.0 * PI
    fun toDegrees(radians: Double): Double = radians * 180.0 / PI
}
