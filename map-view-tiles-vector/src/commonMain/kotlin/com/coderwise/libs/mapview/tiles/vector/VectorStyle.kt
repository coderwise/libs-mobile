package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.ui.graphics.Color

/**
 * What a vector tile looks like: every colour and size the drawing uses, in one value with a
 * sensible default. Change what you want and leave the rest — `VectorStyle(land = Color.Black)`,
 * or `existing.copy(road = { … })`.
 *
 * The lookups are by the feature's `class` tag as OpenMapTiles writes it — "motorway", "wood",
 * "city" — and returning null for one is how a style says *don't draw this*: an unlisted land
 * cover is left as bare ground, an unlisted road is not a road, an unlisted place has no name.
 *
 * A style is read when a tile is decoded, not when it is drawn, because that is when features are
 * grouped into one path per colour. So changing it means decoding the tiles again — which is the
 * cost of a frame being a handful of draw calls, and of a slot being a pure function of its tile.
 */
data class VectorStyle(
    /** Everything a tile does not say anything else about. */
    val land: Color = Color(0xFFF3F1EC),
    val water: Color = Color(0xFFA5CDE3),
    /** Text, and the halo drawn under it so a name survives a road passing beneath. */
    val ink: Color = Color(0xFF3A3A38),
    val halo: Color = Color(0xF2FFFFFF),
    /** Ground cover, by class. */
    val landcover: (kind: String) -> Color? = ::defaultLandcover,
    /** Waterway widths in dp; a river is drawn as a line, a lake as an area. */
    val waterway: (kind: String) -> Float? = ::defaultWaterway,
    /** Road paint and width, by class. */
    val road: (kind: String) -> Road? = ::defaultRoad,
    /** Place-name sizes in sp, by class — how a style says a hamlet is smaller than a city. */
    val place: (kind: String) -> Float? = ::defaultPlace,
    /** Road-name size in sp; null leaves roads unnamed. */
    val roadName: Float? = 10f,
    /**
     * Buildings, and the outline that separates one from the one it abuts. Null for either leaves
     * that half undrawn.
     *
     * The outline is worth having only where a building is big enough to have an edge: below
     * [BUILDING_OUTLINE_MIN_ZOOM] a block of them is a block of specks, and tracing each one is by
     * a wide margin the most expensive thing on the tile — measured at 16 ms of a London z14
     * tile's 47 ms, more than every road on it put together, because a stroke costs what its
     * geometry costs to trace and there is a great deal of building geometry.
     */
    val building: Color? = Color(0xFFDCD5CB),
    val buildingOutline: Color? = Color(0xFFC3B9AC)
) {
    /**
     * [width] is in dp; [casing] is the outline drawn 1 dp wider underneath.
     *
     * A data class on purpose: roads are grouped by the paint they share into one path each, and
     * identity equality would put every road in a group of its own — thousands of draw calls to a
     * tile. Anything a style returns from [road] has to compare by value for the same reason.
     */
    data class Road(val color: Color, val casing: Color, val width: Float)
}

/** Zoom from which buildings are outlined; see [VectorStyle.buildingOutline]. */
const val BUILDING_OUTLINE_MIN_ZOOM = 15

private fun defaultLandcover(kind: String): Color? = when (kind) {
    "wood", "forest" -> Color(0xFFC9E1BE)
    "grass", "park", "meadow", "garden", "pitch", "recreation_ground" -> Color(0xFFD8E9C4)
    "farmland", "farm", "orchard", "vineyard" -> Color(0xFFEDEBD3)
    "residential", "suburb", "neighbourhood" -> Color(0xFFE8E5DF)
    "industrial", "commercial", "retail" -> Color(0xFFE9E0DE)
    "sand", "beach" -> Color(0xFFF5EAC8)
    "ice", "glacier" -> Color(0xFFEAF3F6)
    "cemetery", "hospital", "school", "university" -> Color(0xFFE3E4D4)
    else -> null
}

/** Anything unnamed here is a ditch, and not worth a line. */
private fun defaultWaterway(kind: String): Float? = when (kind) {
    "river" -> 2.5f
    "canal" -> 2f
    "stream" -> 1.2f
    else -> null
}

private fun defaultRoad(kind: String): VectorStyle.Road? = when (kind) {
    "motorway" -> VectorStyle.Road(Color(0xFFF0A93C), Color(0xFFC97F1E), 6f)
    "trunk" -> VectorStyle.Road(Color(0xFFF6BE6A), Color(0xFFCF8F35), 5f)
    "primary" -> VectorStyle.Road(Color(0xFFFBD08A), Color(0xFFCFA255), 4.5f)
    "secondary", "tertiary" -> VectorStyle.Road(Color(0xFFFFFFFF), Color(0xFFB9B1A2), 3.5f)
    "minor", "service", "residential", "unclassified" ->
        VectorStyle.Road(Color(0xFFFFFFFF), Color(0xFFC6BEB0), 2.2f)
    "path", "track", "footway", "cycleway" ->
        VectorStyle.Road(Color(0xFFD8D0C2), Color(0xFFCBC2B2), 1f)
    else -> null // rail, ferry, aerialway: not roads
}

/** Anything not named here — an island, a farm — is not worth the room. */
private fun defaultPlace(kind: String): Float? = when (kind) {
    "country" -> 15f
    "state", "province" -> 12.5f
    "city" -> 14f
    "town" -> 12f
    "village" -> 10.5f
    "suburb", "quarter", "neighbourhood" -> 10f
    "hamlet" -> 9.5f
    else -> null
}
