package com.coderwise.libs.mapview.tiles.vector

import com.coderwise.libs.mapview.tiles.vector.mvt.MvtFeature
import com.coderwise.libs.mapview.tiles.vector.mvt.decodeMvt
import kotlin.math.sqrt

/**
 * What kind of thing the map knows about at a queried point, most specific first.
 *
 * The order is the answer's order: a café is a better answer than the building it is in, which is a
 * better answer than the street outside, which is a better answer than "woodland". Declared
 * most-specific first so [featuresAt] can sort by it directly.
 */
enum class MapFeatureKind { POI, PEAK, BUILDING, WATER, ROAD, LAND }

/**
 * One thing a vector tile says is at (or under) a point.
 *
 * [featureClass] is the raw class tag as the source spells it — `forest`, `motorway`, `lake`. It is
 * data rather than copy, so it is passed through untranslated: there is no fixed list to translate
 * against, since a source may ship classes this library has never heard of.
 *
 * [distance] is a fraction of the tile, and `0f` for an area the point falls inside. A caller that
 * wants metres scales it by the tile's own width on the ground.
 */
data class MapFeature(
    val kind: MapFeatureKind,
    val name: String?,
    val featureClass: String?,
    val distance: Float
)

/**
 * What the tile in [bytes] says is at ([x], [y]) — the "what did I just press on" answer, read from
 * the same data the map is drawn from.
 *
 * [x], [y] and [radius] are all fractions of the tile, because this module knows nothing about the
 * world the tile was cut from: the caller converts a coordinate to a tile and a position in it, and
 * a press radius to the fraction of a tile it covers.
 *
 * Areas answer by containment: a press is in a forest or it is not, so a wood a tile away is not an
 * answer to what is *here*. Lines and points answer within [radius] — a press is never pixel-exact,
 * and a road the width of a finger has to be catchable by one.
 *
 * At most one feature per [MapFeatureKind], best first: a named one beats an unnamed one, and among
 * equals the nearer wins. Decoding is the expensive half and this runs on a gesture rather than on
 * a frame, so call it off the main thread.
 */
fun featuresAt(bytes: ByteArray, x: Float, y: Float, radius: Float): List<MapFeature> {
    val layers = runCatching { decodeMvt(bytes, QUERIED_LAYERS) }.getOrNull() ?: return emptyList()

    val best = HashMap<MapFeatureKind, MapFeature>()
    layers.forEach { layer ->
        if (layer.extent <= 0) return@forEach
        val kind = KINDS[layer.name] ?: return@forEach
        // The press, in this layer's own coordinates: layers of one tile may declare different
        // extents, and a fraction is the only thing they all agree on.
        val px = x * layer.extent
        val py = y * layer.extent
        val reach = radius * layer.extent

        layer.features.forEach { feature ->
            val distance = feature.distanceTo(px, py, reach) ?: return@forEach
            val found = MapFeature(
                kind = kind,
                name = feature.name.takeIf { it.isNotEmpty() },
                featureClass = feature.kind.takeIf { it.isNotEmpty() },
                distance = distance / layer.extent
            )
            val standing = best[kind]
            if (standing == null || found.beats(standing)) best[kind] = found
        }
    }
    return best.values.sortedBy { it.kind.ordinal }
}

/** A named answer beats an unnamed one — a lake's name is the answer, "water" is not — then near beats far. */
private fun MapFeature.beats(other: MapFeature): Boolean {
    val named = name != null
    val otherNamed = other.name != null
    return if (named != otherNamed) named else distance < other.distance
}

/**
 * How far ([px], [py]) is from this feature, or null if it is further than [reach] — except for an
 * area, which answers `0f` when the point is inside it and null when it is not, however close the
 * edge happens to be.
 */
private fun MvtFeature.distanceTo(px: Float, py: Float, reach: Float): Float? = when (type) {
    POLYGON -> if (rings.any { it.contains(px, py) }) 0f else null
    LINE -> rings.minOfOrNull { it.distanceToLine(px, py) }?.takeIf { it <= reach }
    else -> rings.minOfOrNull { it.distanceToPoints(px, py) }?.takeIf { it <= reach }
}

/**
 * Whether ([px], [py]) is inside this ring, by crossing count: a ray cast to the right crosses an
 * exterior boundary an odd number of times from inside it. Holes come as rings of their own and are
 * counted the same way, so a point in the courtyard of a building is outside the building.
 */
private fun FloatArray.contains(px: Float, py: Float): Boolean {
    var inside = false
    val points = size / 2
    var j = points - 1
    for (i in 0 until points) {
        val xi = this[2 * i]
        val yi = this[2 * i + 1]
        val xj = this[2 * j]
        val yj = this[2 * j + 1]
        if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** The nearest point of this polyline to ([px], [py]). */
private fun FloatArray.distanceToLine(px: Float, py: Float): Float {
    var best = Float.MAX_VALUE
    var i = 0
    while (i + 3 < size) {
        best = minOf(best, segmentDistance(px, py, this[i], this[i + 1], this[i + 2], this[i + 3]))
        i += 2
    }
    // A one-point "line" has no segment to measure against, so fall back to the point itself.
    return if (best == Float.MAX_VALUE) distanceToPoints(px, py) else best
}

private fun FloatArray.distanceToPoints(px: Float, py: Float): Float {
    var best = Float.MAX_VALUE
    var i = 0
    while (i + 1 < size) {
        best = minOf(best, hypot(px - this[i], py - this[i + 1]))
        i += 2
    }
    return best
}

private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    // A zero-length segment is a point, and projecting onto it would divide by zero.
    if (lengthSquared == 0f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

private fun hypot(dx: Float, dy: Float) = sqrt(dx * dx + dy * dy)

/** Which layer answers as which kind. A layer not named here is not an answer to "what is here". */
private val KINDS = mapOf(
    "poi" to MapFeatureKind.POI,
    "mountain_peak" to MapFeatureKind.PEAK,
    "building" to MapFeatureKind.BUILDING,
    "water" to MapFeatureKind.WATER,
    "waterway" to MapFeatureKind.WATER,
    "transportation" to MapFeatureKind.ROAD,
    "transportation_name" to MapFeatureKind.ROAD,
    "landcover" to MapFeatureKind.LAND,
    "landuse" to MapFeatureKind.LAND,
    "park" to MapFeatureKind.LAND
)

private val QUERIED_LAYERS = KINDS.keys

private const val POLYGON = 3
private const val LINE = 2
