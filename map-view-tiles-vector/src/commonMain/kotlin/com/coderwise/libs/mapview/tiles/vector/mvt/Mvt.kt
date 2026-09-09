package com.coderwise.libs.mapview.tiles.vector.mvt

/** What a feature's geometry is: a point, a line, or a filled ring. */
enum class GeometryType { UNKNOWN, POINT, LINE, POLYGON }

/**
 * One feature of a Mapbox Vector Tile, pared down to what drawing and querying one need.
 *
 * The three ways of reading a feature's tags are three different prices, and a tile is dense enough
 * that the difference decides whether a pan holds its frame rate:
 *
 * - [featureClass] — the one tag the layer is *styled* by, resolved on its own. Usually
 *   OpenMapTiles' `class`, but not always: a boundary is styled by its `admin_level`, since a
 *   national border and a parish line are the same class. Numbers come through as their plain
 *   string form ("6", not "6.0").
 * - [name] — what a label says, resolved the same cheap way, because the layers that carry labels
 *   want this one tag and nothing else.
 * - [attributes] — everything, as a map. Empty unless the layer was asked for; a string-keyed map
 *   per feature is far too expensive for a layer as dense as `transportation`, where all the style
 *   needs to know is whether this line is a motorway or a ferry.
 *
 * Which layers pay which price is [decodeMvt]'s business.
 */
class MvtFeature(
    val type: GeometryType,
    val geometry: TileGeometry,
    val featureClass: String? = null,
    val name: String? = null,
    val attributes: Map<String, Any?> = emptyMap()
)

/** A layer of a vector tile. [extent] is the tile-local coordinate space its features live in. */
class MvtLayer(val name: String, val extent: Int, val features: List<MvtFeature>)

/**
 * Decodes the [keep] layers of a vector tile. Hand-rolled protobuf: MVT uses a handful of fields,
 * so a reader for varints and length-delimited chunks is enough, and skips everything else. The
 * walk is streaming — no intermediate object graph — because decode-time garbage is what turns into
 * a GC pause in the middle of a pan.
 * https://github.com/mapbox/vector-tile-spec
 *
 * Everything here is opt-in per layer, and a layer costs only what it is asked for:
 *
 * - [keep] — the layers to decode at all. A layer left out is measured and skipped without its
 *   features ever being parsed. Null decodes every layer, which is rarely what a renderer wants.
 * - [classLayers] — layer name to the single tag that layer is styled by, into
 *   [MvtFeature.featureClass].
 * - [nameLayers] — the layers whose features carry a label, into [MvtFeature.name].
 * - [attributeLayers] — the layers whose whole tag set is wanted, into [MvtFeature.attributes].
 *   The expensive one; list a layer here only when nothing narrower will do.
 *
 * Malformed streams are tolerated by stopping early rather than throwing: a partial tile still
 * draws, and a tile that draws most of a city beats a blank square.
 */
fun decodeMvt(
    bytes: ByteArray,
    keep: Set<String>? = null,
    classLayers: Map<String, String>? = null,
    nameLayers: Set<String>? = null,
    attributeLayers: Set<String>? = null
): List<MvtLayer> = parseTile(bytes, keep, attributeLayers, classLayers, nameLayers)
