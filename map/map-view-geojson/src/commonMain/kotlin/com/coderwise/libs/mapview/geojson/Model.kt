package com.coderwise.libs.mapview.geojson

import androidx.compose.runtime.Immutable
import com.coderwise.libs.mapview.LatLon

/**
 * A set of [Feature]s: the document most GeoJSON files are, and what [GeoJson] draws.
 *
 * The model is this module's own and covers what RFC 7946 says a document is made of, without the
 * parts that only a producer cares about (`bbox`, `crs`, foreign members). Coordinates are
 * [LatLon], so the order is the one the rest of the map uses — latitude first — and not the one
 * GeoJSON writes them in.
 */
@Immutable
data class FeatureCollection(val features: List<Feature>)

/**
 * One thing on the map: where it is, and what is known about it.
 *
 * [properties] are whatever the document held, as plain values — [String], [Double] (every number,
 * whole or not), [Boolean], `null`, [List] and [Map] — so an app can read its own fields from
 * them. [id] is the document's `id`, which GeoJSON allows to be a string or a number; a number is
 * written without a fraction when it has none.
 */
@Immutable
data class Feature(
    val geometry: Geometry?,
    val properties: Map<String, Any?> = emptyMap(),
    val id: String? = null
)

/** Where a [Feature] is. */
@Immutable
sealed interface Geometry {
    data class Point(val position: LatLon) : Geometry

    data class MultiPoint(val positions: List<LatLon>) : Geometry

    data class LineString(val positions: List<LatLon>) : Geometry

    data class MultiLineString(val lines: List<List<LatLon>>) : Geometry

    /**
     * Rings of positions: the first is the outside, any others are holes in it. A ring is closed —
     * it ends where it starts — as GeoJSON writes it, though drawing it does not depend on that.
     */
    data class Polygon(val rings: List<List<LatLon>>) : Geometry

    /** Polygons, each a list of rings as in [Polygon]. */
    data class MultiPolygon(val polygons: List<List<List<LatLon>>>) : Geometry

    data class GeometryCollection(val geometries: List<Geometry>) : Geometry
}
