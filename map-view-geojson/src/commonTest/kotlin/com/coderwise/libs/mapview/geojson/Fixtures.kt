package com.coderwise.libs.mapview.geojson

import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.toWorld

/** A closed rectangle, as GeoJSON writes a ring: it ends where it starts. */
internal fun ring(west: Double, south: Double, east: Double, north: Double) = listOf(
    LatLon(south, west), LatLon(south, east), LatLon(north, east), LatLon(north, west), LatLon(south, west)
)

internal fun square(centreLon: Double, centreLat: Double, half: Double) =
    ring(centreLon - half, centreLat - half, centreLon + half, centreLat + half)

internal fun polygon(vararg rings: List<LatLon>) = Geometry.Polygon(rings.toList())

internal fun feature(geometry: Geometry?, id: String? = null) = Feature(geometry, id = id)

internal fun collection(vararg features: Feature) = FeatureCollection(features.toList())

/** How far the shapes are from [at], in degrees of longitude: a world unit is 360 of them. */
internal fun Shapes.degreesTo(at: LatLon): Double? =
    world(at).let { nearest(it[0], it[1]) }?.let { it.distance * 360.0 }

internal fun Shapes.featureAt(at: LatLon): Feature? =
    world(at).let { nearest(it[0], it[1]) }?.feature

private fun world(at: LatLon) = listOf(at).toWorld()
