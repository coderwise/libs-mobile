package com.coderwise.libs.mapview

import kotlin.math.pow

/**
 * A position on the tile grid, in tiles: `(1.5, 2.25)` is a quarter of the way down the middle of
 * tile `(1, 2)`. Fractional, because a coordinate lands inside a tile rather than on one, and
 * `Double` because at zoom 19 a `Float`'s last digit is metres on the ground.
 */
data class TilePoint(val x: Double, val y: Double) {
    /** The tile this point falls in. */
    fun tile(zoom: Int): TileKey = TileKey(zoom, x.toInt(), y.toInt())
}

/**
 * Where this coordinate falls on the tile grid at [zoom] — the projection the whole XYZ scheme is
 * built on, published because a caller with a tile in its hands generally has to cross between the
 * two: to say what a tap landed on, to place a name a tile carries, to fit a camera to a route.
 *
 * Zoom is a `Double` so a camera between levels can ask, and `0.0` gives the unit square — the
 * world in `[0, 1)`, which is the frame to hold anything that has to survive a zoom change.
 *
 * Longitude wraps and latitude is clamped to [MERCATOR_LATITUDE_LIMIT], so a point near the
 * antimeridian or over a pole lands on the grid rather than off it.
 */
fun LatLon.onTileGrid(zoom: Double): TilePoint {
    val tiles = 2.0.pow(zoom)
    return TilePoint(Mercator.x(lon) * tiles, Mercator.y(lat) * tiles)
}

/** The coordinate at tile-grid position ([x], [y]) at [zoom] — the inverse of [onTileGrid]. */
fun tileGridToLatLon(x: Double, y: Double, zoom: Double): LatLon {
    val tiles = 2.0.pow(zoom)
    return LatLon(Mercator.lat(y / tiles), Mercator.lon(x / tiles))
}

/** Where the Web Mercator projection stops: past it a coordinate has no tile to fall in. */
const val MERCATOR_LATITUDE_LIMIT = Mercator.MAX_LAT
