package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A stroke width that widens as the map zooms in: [farthest] at [FARTHEST_ZOOM] and further out,
 * [far] at [FAR_ZOOM], [near] at [NEAR_ZOOM] and closer, linear between the stops and clamped
 * outside them.
 *
 * One fixed width cannot serve both ends of the tile pyramid. The width that reads as a street at
 * z16 draws the whole trunk network as a solid white mat at z8, where a motorway should be a
 * hairline; a width thin enough for z8 is a scratch at z17.
 *
 * [farthest] exists because two stops were not enough at the continental end either. A z5 tile
 * carries every motorway in western Europe, and holding them at the z10 width painted the Rhine
 * delta as one white slab — the network at that zoom is a diagram of where the country is, and
 * wants a line about half as wide. It defaults to [far], so a paint that has nothing to say about
 * the far end keeps its old two-stop ramp exactly.
 *
 * Resolved against the zoom of the tile being drawn rather than the live camera, so a road holds
 * its width through a pinch and steps only at the boundaries where the whole tile grid is replaced
 * underneath it anyway.
 */
data class ZoomWidth(val far: Dp, val near: Dp = far, val farthest: Dp = far) {

    /** This width at integer [zoom]. */
    fun at(zoom: Int): Dp = when {
        zoom <= FARTHEST_ZOOM -> farthest
        zoom < FAR_ZOOM ->
            farthest + (far - farthest) *
                ((zoom - FARTHEST_ZOOM).toFloat() / (FAR_ZOOM - FARTHEST_ZOOM).toFloat())

        zoom >= NEAR_ZOOM -> near
        else -> far + (near - far) * ((zoom - FAR_ZOOM).toFloat() / (NEAR_ZOOM - FAR_ZOOM).toFloat())
    }

    /**
     * The wide end — ranks one paint against another without having to pick a zoom to compare at.
     * [farthest] is by construction the narrow end of the ramp, so it takes no part in the answer.
     */
    val widest: Dp get() = maxOf(far, near)

    companion object {
        /**
         * The ramp's three stops. Zoomed out past [FARTHEST_ZOOM] widths stop shrinking; zoomed in
         * past [NEAR_ZOOM] they stop growing.
         */
        const val FARTHEST_ZOOM = 5
        const val FAR_ZOOM = 10
        const val NEAR_ZOOM = 17

        /** No stroke at all — the default for [LayerStyle.casingWidth]. */
        val None = ZoomWidth(0.dp)
    }
}

/**
 * How one named vector layer is painted. A null [fill]/[stroke] disables that draw pass.
 *
 * Widths are [ZoomWidth]s of [Dp], resolved against both the zoom and the display when the tile is
 * drawn: a width in raw pixels would draw a road at a third of its intended thickness on a phone
 * and three times it on a low-density screen, which is how the roads came to be hairlines.
 *
 * [casing] is a second, wider stroke drawn under [stroke] from the same geometry — the two-pass
 * trick every road map uses, and the only way a white road is visible on pale land. The OMT schema
 * has no casing layer of its own to style: there is one `transportation` layer, and a casing is
 * something the style draws, not something the tile ships.
 *
 * [cap] and [join] default to round, which is what a road wants and what every line this style
 * currently draws is. Compose's own defaults are butt and miter: a butt cap leaves a square end on
 * every cul-de-sac and on every road that stops at the junction it meets, and a miter join throws a
 * spike off a hairpin bend up to four times the width of the road it belongs to.
 *
 * A data class on purpose: features are grouped by the paint they share into one path each, so a
 * paint that compared by identity would put every feature in a group of its own — thousands of draw
 * calls to a tile.
 */
data class LayerStyle(
    val fill: Color? = null,
    val stroke: Color? = null,
    val strokeWidth: ZoomWidth = ZoomWidth(1.dp),
    val casing: Color? = null,
    val casingWidth: ZoomWidth = ZoomWidth.None,
    val cap: StrokeCap = StrokeCap.Round,
    val join: StrokeJoin = StrokeJoin.Round,
    /**
     * Zoom below which this paint draws no line at all — neither [stroke] nor [casing].
     *
     * Distinct from letting [ZoomWidth] taper a line away, because a hairline is not cheap: a
     * stroke costs what its geometry costs to trace, not what it covers, so half a dp over every
     * ring on the tile costs the same as half a centimetre would. The building outline is the case
     * this exists for — measured at 16 ms of a London z14 tile's 47 ms, more than every road on
     * that tile put together, to outline specks.
     */
    val strokeMinZoom: Int = 0,
    /**
     * Zoom below which this line is drawn once, in [casing]'s colour at [strokeWidth], instead of
     * as the [casing]-under-[stroke] pair. Ignored by a paint with no [casing].
     *
     * The picture is the first reason. Zoomed out far enough that one tile holds a continent, the
     * white-road-with-a-dark-edge treatment stops reading as roads with edges: the edges of
     * neighbouring roads meet, and the Rhine delta comes out as a pale mat with a dark fringe. A
     * single line in the edge colour is what an atlas draws at that scale, and it is what the eye
     * reads back as a road network.
     *
     * The cost is the second reason, and the larger one. A casing is the same geometry traced a
     * second time, and a stroke costs what its geometry costs to trace — so the pair costs exactly
     * twice the line. That is affordable on a z14 tile holding one suburb's streets. On a z5 tile
     * holding every motorway between the Alps and the North Sea, `transportation` measured 32 ms of
     * the tile's 38 ms total, half of it the casing — paid again for every tile a pan brings on.
     */
    val casingMinZoom: Int = 0,
    /**
     * What the single line below [casingMinZoom] is painted in. Null, the default, is [casing] —
     * the collapsed line is the edge colour, standing in for both passes.
     *
     * Worth setting where the collapse is a zoom the user pans and pinches across rather than one
     * they fly over. A casing and its road do not average to the casing's colour: at the zoom a
     * side street collapses, two thirds of its width was white, so what the eye had been reading is
     * something much closer to the land than to the edge. Painting the collapsed line in the edge
     * colour makes the street *darken* as you zoom out — the one direction it should not — and the
     * step back is a visible flash of white the moment the casing returns.
     *
     * The literal average is nearly the land's own colour, which would leave the street invisible
     * over the buildings under it, so this is a compromise rather than a computation: light enough
     * that the step is small, dark enough that the street is still a street.
     */
    val coarse: Color? = null
)

/**
 * What a vector tile looks like: every colour and width the drawing uses, in one value with a
 * sensible default. Change what you want and leave the rest — `VectorStyle(background = ink)`, or
 * `VectorStyle().copy(building = null)`.
 *
 * The per-layer lookups take the feature's `class` tag as OpenMapTiles writes it — "motorway",
 * "wood", "residential" — or, for [boundary], its `admin_level`. A feature whose tag the tile did
 * not carry arrives as the empty string, which is how each default answers with the layer's own
 * paint rather than a class's. Two different nothings come back out:
 *
 * - `LayerStyle()` — *draw nothing*. An unclassified land use is bare land, not a grey wash.
 * - `null` — *no opinion*, so the geometry falls to [polygonFallback] or [lineFallback].
 *
 * Deliberately small: flat fills and zoom-ramped strokes, no data-driven expressions beyond a
 * feature's class.
 *
 * A data class, so two styles built the same way are equal and a decoded-tile cache keyed on one
 * survives being handed another. That holds only while the lookups are the shared defaults or other
 * function *references* — a lambda literal is a fresh object every time it is evaluated, so a style
 * built with one inside a composition would throw the cache away on every frame.
 */
data class VectorStyle(
    /**
     * OpenMapTiles has no land polygon: land is the implied background, with the water layer drawn
     * on top.
     */
    val background: Color = LAND,
    /**
     * Label ink, and the halo drawn under it so a name survives a road passing beneath. Tied to the
     * style rather than the app theme so labels stay legible on the tiles they sit on: the rendered
     * map does not follow the app's dark mode, so neither can its labels.
     */
    val ink: Color = Color(0xFF2B2B2B),
    val halo: Color = Color(0xFFFFFFFF),
    /**
     * Water labels (lakes, rivers) — blue by cartographic convention, but a deep one: the muted
     * slate this replaced was barely darker than the land it was read against, and italic type has
     * thin strokes to lose.
     */
    val waterInk: Color = Color(0xFF15495F),
    val water: LayerStyle? = LayerStyle(fill = WATER),
    val waterway: LayerStyle? = LayerStyle(stroke = WATER, strokeWidth = ZoomWidth(0.8.dp, 2.2.dp)),
    val park: LayerStyle? = LayerStyle(fill = PARKLAND),
    /**
     * A building has to read as a building at the zoom where buildings are the whole map. The
     * near-land tint this replaced was 0.08 off the ground it stood on, so a city block came out
     * flat; the outline is what separates one building from the one it abuts. Areas are otherwise
     * fill-only — stroking a polygon also traces the artificial straight edges where MVT clips it
     * at a tile boundary — and buildings can be the exception because they are small enough to fall
     * inside a tile's buffer rather than be cut by it, so their outline is the building's own.
     *
     * `copy(building = LayerStyle(fill = …))` drops the outline at every zoom, which is worth
     * having to hand: it is far and away the most expensive thing the renderer draws.
     */
    val building: LayerStyle? = LayerStyle(
        fill = BUILDING,
        stroke = BUILDING_OUTLINE,
        // Thin, and thinner still zoomed out: buildings arrive around z14 as specks, and an
        // outline with any weight at that size is all you'd see.
        strokeWidth = ZoomWidth(0.25.dp, 0.7.dp),
        // And below BUILDING_OUTLINE_MIN_ZOOM, not at all — a block of specks reads as a block
        // whether or not each speck is traced.
        strokeMinZoom = BUILDING_OUTLINE_MIN_ZOOM
    ),
    /**
     * Sources that ship a separate casing layer (rather than leaving it to the style) get the same
     * edge; OpenMapTiles does not, hence the casing on each road paint.
     */
    val roadCasing: LayerStyle? = LayerStyle(
        stroke = ROAD_CASING,
        strokeWidth = ZoomWidth(casingFor(MINOR_FAR), casingFor(MINOR_NEAR))
    ),
    /** What the ground is made of — not all greenery: sand, bare rock and ice are in here too. */
    val landcover: (kind: String) -> LayerStyle? = ::defaultLandcover,
    /** What the ground is used for. */
    val landuse: (kind: String) -> LayerStyle? = ::defaultLanduse,
    /** The road hierarchy, plus the things OpenMapTiles files among the roads that are not roads. */
    val road: (kind: String) -> LayerStyle? = ::defaultRoad,
    /** Airport ground and the strips laid on it. */
    val aeroway: (kind: String) -> LayerStyle? = ::defaultAeroway,
    /** Borders, keyed by `admin_level` rather than by class. */
    val boundary: (adminLevel: String) -> LayerStyle? = ::defaultBoundary,
    /** Place-name sizes in sp, by class — how a style says a hamlet is smaller than a city. */
    val place: (kind: String) -> Float? = ::defaultPlace,
    /** Road-name size in sp; null leaves roads unnamed. */
    val roadName: Float? = 10f,
    /** What an unrecognised layer's geometry falls to. */
    val polygonFallback: LayerStyle? = LayerStyle(fill = Color(0x11000000)),
    val lineFallback: LayerStyle? = LayerStyle(stroke = Color(0x33000000), strokeWidth = ZoomWidth(1.dp))
) {
    /**
     * The paint for one feature: its layer's, narrowed by its class where the layer has classes.
     * Null leaves it to the geometry fallbacks.
     */
    fun paint(layer: String, featureClass: String?): LayerStyle? {
        val kind = featureClass.orEmpty()
        return when (layer) {
            "water" -> water
            "waterway" -> waterway
            "park" -> park
            "building" -> building
            "transportation" -> road(kind)
            "transportation_casing" -> roadCasing
            "landcover" -> landcover(kind)
            "landuse" -> landuse(kind)
            "aeroway" -> aeroway(kind)
            "boundary" -> boundary(kind)
            else -> null
        }
    }

    companion object {
        /**
         * Each layer whose features need a tag resolved during decode, and which tag that is.
         * Boundaries are why it is not simply `class`: OpenMapTiles gives every one of them the
         * same class and tells them apart by `admin_level`, so a national border and a parish line
         * arrive indistinguishable otherwise.
         */
        val CLASS_AWARE_LAYERS: Map<String, String> = mapOf(
            "aeroway" to "class",
            "boundary" to "admin_level",
            "landcover" to "class",
            "landuse" to "class",
            "transportation" to "class"
        )
    }
}

// Land, water and the roads on them.
private val LAND = Color(0xFFF2EFE9)
private val WATER = Color(0xFFAAD3DF)
private val ROAD = Color(0xFFFFFFFF)

// Dark enough to read as an edge against both the land and the greens it crosses -- the road
// itself is white, so the casing is the only thing that says where it is -- but no darker: a full
// page of roads outlined in near-black reads as a circuit board.
private val ROAD_CASING = Color(0xFFA39D93)

// A service road is a driveway, an alley, a parking aisle — the bottom of the road hierarchy, and
// in a city tile a great many of them. Lighter than ROAD_CASING so they sit behind the streets
// rather than beside them: this is the colour of their edge where they have one, and below
// SIDE_STREET_CASING_MIN_ZOOM it is the whole line, so a mews reads as a mews rather than as
// another road off the high street.
private val SERVICE_CASING = Color(0xFFC8C2B7)

// What those two draw as below SIDE_STREET_CASING_MIN_ZOOM, where they are a single line rather
// than a road with an edge (see LayerStyle.coarse). Lighter than the edges above, because two
// thirds of the width they replace was the white of the road: painted in the edge's own colour the
// street darkened as you zoomed out, and flashed white again the moment the casing came back.
private val SIDE_STREET_COARSE = Color(0xFFC6BFB3)
private val SERVICE_COARSE = Color(0xFFD7D1C7)

// Tracks and footpaths are not streets and must not read as ones: no white fill, no casing, just a
// thin muted line that a park path can wear without becoming a road.
//
// Muted rather more than it used to be. A city centre is made of footways — alleys, courtyards,
// pedestrianised streets, steps — and at z14 they were an eighth of every pixel on the map,
// sampled: four times the ink of the side streets and seven times the road casing, all of it
// darker than either. It read as a scribble under the map rather than as detail on it. This is the
// weight of a path at the zoom it is worth following, not the zoom it is worth counting.
private val PATH = Color(0xFFCCC1B2)

// Railways come through `transportation` too, and were being painted as white streets. Lightened
// with the paths and for the same reason: the fan of lines outside a terminus is the densest dark
// thing on a city tile, and it is not what the map is about at that scale.
private val RAIL = Color(0xFFB4AEA5)

// A shipping line is not a road: in the water's own colour family so it reads as a crossing.
private val FERRY = Color(0xFF6E9DB3)

// Natural ground cover, in the greens and earths it is actually made of. One flat green across the
// whole layer painted glaciers and deserts as meadow.
private val WOOD = Color(0xFFC6DFB4)
private val GRASS = Color(0xFFD3E8C4)
private val FARMLAND = Color(0xFFE9E3CF)
private val SAND = Color(0xFFF2E4C7)
private val ICE = Color(0xFFEAF2F5)
private val ROCK = Color(0xFFE2DED6)
private val WETLAND = Color(0xFFD2E6DA)
private val PARKLAND = Color(0xFFCDE6BE)

// Human land use is a tint on the land, not a colour of its own: an area this size sits under
// everything else on the map, and only has to say "this block is built up", "this one is works" --
// loudly enough to see, quietly enough to read street names over.
private val RESIDENTIAL = Color(0xFFEAE6DE)
private val COMMERCIAL = Color(0xFFF0E4E0)
private val INDUSTRIAL = Color(0xFFE7E3E8)
private val INSTITUTION = Color(0xFFEFE8DD)
private val CEMETERY = Color(0xFFDDE7D5)
private val QUARRY = Color(0xFFE0DCD4)

private val BUILDING = Color(0xFFDCD5CB)
private val BUILDING_OUTLINE = Color(0xFFC3B9AC)

// Airport ground, and the strips laid on it. Unstyled, these fell to the geometry fallbacks and
// drew a runway as a translucent black scratch across a gray smudge.
private val AIRPORT_GROUND = Color(0xFFE8E6E1)
private val RUNWAY = Color(0xFFDAD8D3)
private val TAXIWAY = Color(0xFFE2E0DB)

// Borders by rank. A country line is the darkest thing on the map after the labels; the ranks below
// it step back towards the land, and the local ones don't draw at all.
private val BOUNDARY = Color(0xFF9E9E9E)
private val BORDER_COUNTRY = Color(0xFF8A8A93)
private val BORDER_STATE = Color(0xFFA2A2AB)
private val BORDER_COUNTY = Color(0xFFBCBCC3)

private const val CASING_EDGE_RATIO = 0.6f
private val CASING_MIN_EDGE = 0.6.dp
private val CASING_MAX_EDGE = 2.4.dp

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

/** The side street the layer falls back to, and the width the casing layer is drawn at. */
private val MINOR_FAR = 0.7.dp
private val MINOR_NEAR = 3.4.dp

/**
 * The casing under a road [w] wide: the road plus an edge proportional to it, clamped at both ends.
 * Below [CASING_MIN_EDGE] the dark edge disappears into the antialiasing and a hairline road has
 * nothing to be seen by; above [CASING_MAX_EDGE] a motorway at z17 stops looking like a road with
 * an edge and starts looking like one drawn in black.
 */
private fun casingFor(w: Dp): Dp =
    w + (w * CASING_EDGE_RATIO).coerceIn(CASING_MIN_EDGE, CASING_MAX_EDGE)

/**
 * A road of the given width, with the casing that gives it an edge — and, below
 * [ROAD_CASING_MIN_ZOOM], a single line in the casing's colour instead of the pair (see
 * [LayerStyle.casingMinZoom]). [ROAD_FAR_TAPER] is what that line is scaled to: at the zooms where
 * a tile spans a country the network has to read as lines on the land, not as a mat over it.
 */
private fun roadPaint(
    far: Dp,
    near: Dp,
    casingMinZoom: Int = ROAD_CASING_MIN_ZOOM,
    casing: Color = ROAD_CASING,
    coarse: Color? = null
) = LayerStyle(
    stroke = ROAD,
    strokeWidth = ZoomWidth(far, near, farthest = far * ROAD_FAR_TAPER),
    casing = casing,
    casingWidth = ZoomWidth(casingFor(far), casingFor(near)),
    casingMinZoom = casingMinZoom,
    coarse = coarse
)

/** A line that is not a road: no white fill, no casing. */
private fun trackPaint(color: Color, far: Dp, near: Dp) =
    LayerStyle(stroke = color, strokeWidth = ZoomWidth(far, near))

private val MINOR_ROAD =
    roadPaint(MINOR_FAR, MINOR_NEAR, SIDE_STREET_CASING_MIN_ZOOM, coarse = SIDE_STREET_COARSE)

/**
 * The road hierarchy. Without it every line in `transportation` drew at one weight: a farm track as
 * wide as an autobahn at z15, and at z8 the whole trunk network merged into a single white mat.
 *
 * Both spellings of each class are listed. OpenMapTiles collapses the OSM highway tags into `minor`
 * and `path`, but not every source does: a source that spells them out in full has to land on the
 * same paint.
 *
 * Anything unlisted — including a road whose class the tile does not name — is a side street, the
 * safest guess.
 */
private fun defaultRoad(kind: String): LayerStyle? = when (kind) {
    "motorway" -> roadPaint(1.8.dp, 7.0.dp)
    "trunk" -> roadPaint(1.6.dp, 6.4.dp)
    "primary" -> roadPaint(1.4.dp, 6.0.dp)
    "secondary" -> roadPaint(1.1.dp, 5.0.dp)
    "tertiary" -> roadPaint(0.9.dp, 4.2.dp)
    "service" -> roadPaint(
        0.6.dp, 2.4.dp, SIDE_STREET_CASING_MIN_ZOOM,
        casing = SERVICE_CASING, coarse = SERVICE_COARSE
    )
    "track" -> trackPaint(PATH, 0.5.dp, 1.8.dp)
    "path", "footway", "cycleway", "pedestrian", "steps" -> trackPaint(PATH, 0.5.dp, 1.6.dp)
    "rail", "transit" -> trackPaint(RAIL, 0.5.dp, 1.6.dp)
    "ferry" -> trackPaint(FERRY, 0.8.dp, 1.4.dp)
    else -> MINOR_ROAD
}

/**
 * What the ground is made of. OpenMapTiles' `landcover` is not all greenery: it carries sand, bare
 * rock and glacier ice in the same layer as woodland. Cover the tile doesn't classify is most
 * likely rough green of some kind.
 */
private fun defaultLandcover(kind: String): LayerStyle? = when (kind) {
    "wood", "forest" -> LayerStyle(fill = WOOD)
    "grass", "meadow", "grassland", "heath", "scrub" -> LayerStyle(fill = GRASS)
    "farmland", "farm", "orchard", "vineyard" -> LayerStyle(fill = FARMLAND)
    "sand", "beach" -> LayerStyle(fill = SAND)
    "ice", "glacier", "snow" -> LayerStyle(fill = ICE)
    "rock", "bare_rock", "scree" -> LayerStyle(fill = ROCK)
    "wetland", "marsh", "swamp" -> LayerStyle(fill = WETLAND)
    else -> LayerStyle(fill = GRASS)
}

/**
 * What the ground is used for. Every one of these was drawn in park green, so a city centre and the
 * industrial estate beside it both read as lawn.
 *
 * A land use the table doesn't name is left as bare land — `LayerStyle()`, which draws nothing,
 * rather than null, which would let the polygon fallback wash it grey. `landuse` is residential,
 * industrial, retail, military, quarry: there is no one colour that is right for all of them.
 */
private fun defaultLanduse(kind: String): LayerStyle? = when (kind) {
    "residential", "suburb", "neighbourhood", "quarter", "garages" -> LayerStyle(fill = RESIDENTIAL)
    "commercial", "retail" -> LayerStyle(fill = COMMERCIAL)
    "industrial", "railway" -> LayerStyle(fill = INDUSTRIAL)
    "hospital", "school", "university", "college", "kindergarten", "library" ->
        LayerStyle(fill = INSTITUTION)

    "cemetery" -> LayerStyle(fill = CEMETERY)
    "quarry" -> LayerStyle(fill = QUARRY)
    // Sports and recreation are green space whatever they are filed under.
    "pitch", "playground", "track", "stadium", "theme_park", "zoo", "golf_course",
    "recreation_ground", "dog_park" -> LayerStyle(fill = PARKLAND)

    else -> LayerStyle()
}

/**
 * Airports. Each strip gets both a fill and a stroke because sources disagree about the geometry: a
 * runway arrives as a line from one and as the polygon of the strip itself from another, and it has
 * to read the same either way — a wide pale band across the airport ground.
 *
 * An aeroway feature whose class the tile doesn't give is airport ground, with a faint line for
 * anything linear, since a fill alone would drop it.
 */
private fun defaultAeroway(kind: String): LayerStyle? = when (kind) {
    "aerodrome", "apron", "helipad" -> LayerStyle(fill = AIRPORT_GROUND)
    "runway" -> LayerStyle(fill = RUNWAY, stroke = RUNWAY, strokeWidth = ZoomWidth(1.2.dp, 6.0.dp))
    "taxiway" -> LayerStyle(fill = TAXIWAY, stroke = TAXIWAY, strokeWidth = ZoomWidth(0.5.dp, 2.0.dp))
    else -> LayerStyle(
        fill = AIRPORT_GROUND,
        stroke = TAXIWAY,
        strokeWidth = ZoomWidth(0.5.dp, 1.5.dp)
    )
}

/**
 * Borders, by how much country is on either side of them. Every boundary in OpenMapTiles carries
 * the same class and differs only by `admin_level`, so they all drew as one gray line on top of
 * everything — which at street zoom, where the parish and ward lines arrive, laid a gray mesh over
 * the map.
 *
 * Municipal and below draw nothing. They are administrative trivia at the zoom they appear at, and
 * there are enough of them to net the map over. Nothing, not a zero width — a zero-width stroke is
 * a hairline, not a line you can't see.
 *
 * A boundary whose rank the tile doesn't give is drawn as it always was, since there is nothing
 * better to go on.
 */
private fun defaultBoundary(adminLevel: String): LayerStyle? = when (adminLevel) {
    "2" -> LayerStyle(stroke = BORDER_COUNTRY, strokeWidth = ZoomWidth(0.9.dp, 1.6.dp))
    "3", "4" -> LayerStyle(stroke = BORDER_STATE, strokeWidth = ZoomWidth(0.7.dp, 1.2.dp))
    "5", "6" -> LayerStyle(stroke = BORDER_COUNTY, strokeWidth = ZoomWidth(0.6.dp, 0.9.dp))
    "7", "8", "9", "10" -> LayerStyle()
    else -> LayerStyle(stroke = BOUNDARY, strokeWidth = ZoomWidth(0.8.dp))
}

/**
 * Zoom from which buildings are outlined. At z15 a building is big enough that its own edge is what
 * separates it from the one it abuts; below that the outline is most of the tile's draw cost and
 * none of its picture (see [LayerStyle.strokeMinZoom]).
 *
 * Gating the *fill* the same way was tried and reverted: it measured as nothing. The outline is the
 * expensive half — stroking a ring builds a stroke outline from it — and filling a few thousand
 * small convex polygons is something Skia's scanline filler barely notices.
 */
private const val BUILDING_OUTLINE_MIN_ZOOM = 15

/**
 * Zoom below which a road is one line in the casing's colour rather than a white road with an edge
 * — see [LayerStyle.casingMinZoom] for both halves of the reason.
 *
 * Nine, because that is about where a tile stops being a place and starts being a region: at z9 a
 * tile is a county and its roads are still roads; at z8 it is a country and they are a diagram of
 * one.
 *
 * That is for the roads that carry the map's structure. A side street keeps its single line for
 * much longer — see [SIDE_STREET_CASING_MIN_ZOOM].
 */
private const val ROAD_CASING_MIN_ZOOM = 9

/**
 * The same, for the streets that make up the bulk of a city tile: service roads and everything the
 * fallback catches, which is residential, unclassified and living-street — the OpenMapTiles `minor`
 * class and the great majority of the geometry in `transportation`.
 *
 * They are the road network's long tail in both senses. A casing is the same geometry traced a
 * second time, so the pair costs exactly twice the line, and it is these classes that the doubling
 * is paid on: collapsing every casing below z15 took a z14 London pan from 276 frames to 458 and
 * its median frame from 53 ms to 38 ms.
 *
 * Fifteen, because that is where a side street becomes something you follow rather than something
 * you see the pattern of. Below it the hierarchy reads *better* for the collapse: the through roads
 * keep their white-with-an-edge and the side streets fall back to a plain line, which is the
 * distinction an atlas draws at that scale anyway.
 */
private const val SIDE_STREET_CASING_MIN_ZOOM = 15

/**
 * What a road's [ZoomWidth.far] width is multiplied by at [ZoomWidth.FARTHEST_ZOOM]. Half, measured
 * by eye against the alternative: held at the z10 width, the motorways of the Low Countries meet
 * each other on a z5 tile and fill it.
 */
private const val ROAD_FAR_TAPER = 0.5f
