package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The style is data, so it can be asserted here; what the renderer does with it cannot — building a
 * [androidx.compose.ui.graphics.Path] needs Skia loaded, which a plain JVM test doesn't have.
 */
class VectorStyleTest {

    @Test
    fun `a road carries its own casing - wider and darker than the road`() {
        val road = assertNotNull(VectorStyle().paint("transportation", null))

        // The OpenMapTiles schema ships no casing layer: a road drawn as a single white line has
        // no edge at all on pale land, so the style has to draw the second, wider pass itself.
        val casing = assertNotNull(road.casing, "roads have no casing to draw")
        assertEquals(Color.White, road.stroke)
        // At every zoom, not only the one the widths were tuned at — the road and its casing ramp
        // separately, and a ramp that converged would erase the edge at one end of the pyramid.
        for (zoom in 4..20) {
            assertTrue(
                road.casingWidth.at(zoom) > road.strokeWidth.at(zoom),
                "at z$zoom casing ${road.casingWidth.at(zoom)} is not wider than road ${road.strokeWidth.at(zoom)}"
            )
        }
        assertTrue(casing.brightness() < road.stroke!!.brightness())
    }

    @Test
    fun `the road casing is dark enough to read against the land it crosses`() {
        val style = VectorStyle()
        val casing = assertNotNull(assertNotNull(style.paint("transportation", null)).casing)

        // Not a formal contrast ratio — just far enough from the background that the edge of a
        // road is visible rather than implied, and near enough that a page of them doesn't read as
        // a circuit board. The near-white gray this started from was 0.06 away from the land.
        val gap = style.background.brightness() - casing.brightness()
        assertTrue(gap > 0.25f, "casing only $gap darker than the land it is drawn on")
        assertTrue(gap < 0.5f, "casing $gap darker than the land is heavier than a road needs")
    }

    @Test
    fun `roads are wide enough to aim a thumb at where the map is read`() {
        val road = assertNotNull(VectorStyle().paint("transportation", null))

        // Widths are Dp, resolved per display: as raw pixels these were a third of this on a phone,
        // which is how the roads came to be hairlines in the first place. Asserted at street zoom —
        // at z10 a side street is deliberately a hairline, because that is all it should be there.
        assertTrue(road.strokeWidth.at(16).value >= 1.5f, "roads are only ${road.strokeWidth.at(16)} wide")
    }

    @Test
    fun `a road is a hairline zoomed out and a ribbon zoomed in`() {
        val motorway = assertNotNull(VectorStyle().paint("transportation", "motorway"))

        // One flat width cannot serve both ends of the pyramid: the width that reads as a street at
        // z16 drew the whole trunk network as a solid white mat at z8.
        assertTrue(
            motorway.strokeWidth.at(16) > motorway.strokeWidth.at(8) * 2f,
            "a motorway is ${motorway.strokeWidth.at(8)} at z8 and only ${motorway.strokeWidth.at(16)} at z16"
        )
        // Clamped at both ends, so zooming further doesn't keep thinning or fattening the line.
        assertEquals(motorway.strokeWidth.at(ZoomWidth.FARTHEST_ZOOM), motorway.strokeWidth.at(2))
        assertEquals(motorway.strokeWidth.at(ZoomWidth.NEAR_ZOOM), motorway.strokeWidth.at(21))
        // And still tapering between the two far stops: the continental end is thinner than the
        // regional one, which is what keeps a z5 tile of the Low Countries from filling in.
        assertTrue(
            motorway.strokeWidth.at(ZoomWidth.FARTHEST_ZOOM) < motorway.strokeWidth.at(ZoomWidth.FAR_ZOOM),
            "a motorway is the same width at z${ZoomWidth.FARTHEST_ZOOM} as at z${ZoomWidth.FAR_ZOOM}"
        )
    }

    @Test
    fun `a road loses its casing at the zooms where a tile holds a continent`() {
        val motorway = assertNotNull(VectorStyle().paint("transportation", "motorway"))

        // Below the gate the road is one line, not a casing under a stroke — half the geometry
        // tracing, on the tiles that have by far the most of it to trace.
        assertTrue(motorway.casingMinZoom > 0, "a road's casing is never collapsed")
        assertTrue(
            motorway.casingMinZoom in (ZoomWidth.FARTHEST_ZOOM + 1)..ZoomWidth.FAR_ZOOM,
            "the casing gate at z${motorway.casingMinZoom} is outside the zooms it was measured for"
        )
        // A paint with no casing has nothing to collapse and must not claim a band of its own.
        val path = assertNotNull(VectorStyle().paint("transportation", "path"))
        assertEquals(0, path.casingMinZoom)
    }

    @Test
    fun `the road hierarchy is visible in the widths`() {
        val style = VectorStyle()
        // Without this every line in `transportation` drew at one weight — a farm track as wide as
        // an autobahn. Checked at a zoom where all of these are on the map at once.
        val order = listOf("motorway", "trunk", "primary", "secondary", "tertiary", "minor", "service")
            .map { it to assertNotNull(style.paint("transportation", it), "no paint for $it") }

        order.zipWithNext { (higherName, higher), (lowerName, lower) ->
            assertTrue(
                higher.strokeWidth.at(15) > lower.strokeWidth.at(15),
                "$higherName (${higher.strokeWidth.at(15)}) is not wider than $lowerName (${lower.strokeWidth.at(15)})"
            )
        }
        // Every one of them still gets its edge.
        order.forEach { (name, paint) ->
            assertNotNull(paint.casing, "$name has no casing")
            assertTrue(paint.casingWidth.at(15) > paint.strokeWidth.at(15), "$name has no visible edge")
        }
    }

    @Test
    fun `a path or a railway is not painted as a street`() {
        val style = VectorStyle()
        val road = assertNotNull(style.paint("transportation", null))

        // OpenMapTiles files these under `transportation` too, so without their own paint a footpath
        // through a park and a railway line both drew as white streets with a road's casing.
        listOf("path", "footway", "track", "cycleway", "steps", "rail", "transit").forEach { klass ->
            val paint = assertNotNull(style.paint("transportation", klass), "no paint for $klass")
            assertNull(paint.casing, "$klass is drawn with a road's casing")
            assertTrue(paint.stroke != Color.White, "$klass is drawn in road white")
            assertTrue(paint.strokeWidth.at(17) < road.strokeWidth.at(17), "$klass is as wide as a street")
        }
    }

    @Test
    fun `a ferry route is not painted as a road`() {
        val style = VectorStyle()
        val road = assertNotNull(style.paint("transportation", null))
        // OpenMapTiles files ferries under transportation, so without this they were drawn with a
        // motorway's weight and casing — a highway across the sea.
        val ferry = assertNotNull(style.paint("transportation", "ferry"))

        assertTrue(ferry.strokeWidth.at(12) < road.strokeWidth.at(12))
        assertNull(ferry.casing)
        assertTrue("transportation" in VectorStyle.CLASS_AWARE_LAYERS)
    }

    @Test
    fun `a city block is not painted as parkland`() {
        val style = VectorStyle()
        val parkland = assertNotNull(assertNotNull(style.paint("park", null)).fill)

        // OpenMapTiles files residential, commercial and industrial areas under `landuse`, which
        // shared one green with `park` — so every built-up area on the map read as lawn.
        listOf("residential", "suburb", "commercial", "retail", "industrial", "quarry").forEach { klass ->
            val fill = assertNotNull(
                assertNotNull(style.paint("landuse", klass), "no paint for $klass").fill,
                "$klass draws nothing"
            )
            assertTrue(fill != parkland, "$klass is painted as parkland")
            assertTrue(!fill.isGreen, "$klass is painted a green")
        }
        assertTrue("landuse" in VectorStyle.CLASS_AWARE_LAYERS)
    }

    @Test
    fun `a built-up area is a tint on the land rather than a colour over it`() {
        val style = VectorStyle()
        val residential = assertNotNull(assertNotNull(style.paint("landuse", "residential")).fill)

        // It sits under everything else on the map: far enough from the land to say the block is
        // built up, near enough that a street name stays readable over it.
        val gap = style.background.brightness() - residential.brightness()
        assertTrue(gap > 0.01f, "a built-up block is only $gap off the bare land — invisible")
        assertTrue(gap < 0.06f, "a built-up block is $gap off the bare land — it shouts")
    }

    @Test
    fun `sand and ice are not painted as meadow`() {
        val style = VectorStyle()
        val grass = assertNotNull(assertNotNull(style.paint("landcover", null)).fill)

        // `landcover` is not all greenery: the same layer carries deserts and glaciers, and the
        // one flat green this replaced drew both of them as pasture.
        listOf("sand", "beach", "ice", "glacier", "rock", "scree").forEach { klass ->
            val fill = assertNotNull(
                assertNotNull(style.paint("landcover", klass), "no paint for $klass").fill
            )
            assertTrue(fill != grass, "$klass is painted as grass")
            assertTrue(!fill.isGreen, "$klass is painted a green")
        }
        assertTrue("landcover" in VectorStyle.CLASS_AWARE_LAYERS)
    }

    @Test
    fun `woods read darker than the grass around them`() {
        val style = VectorStyle()
        val wood = assertNotNull(assertNotNull(style.paint("landcover", "wood")).fill)
        val grass = assertNotNull(assertNotNull(style.paint("landcover", "grass")).fill)

        assertTrue(wood.isGreen && grass.isGreen)
        assertTrue(wood.brightness() < grass.brightness(), "a forest is no darker than a meadow")
    }

    @Test
    fun `a land use the table doesn't name is left as bare land`() {
        // No one colour is right for every human land use, so an unrecognised one draws nothing
        // and the land shows through — rather than the geometry fallback's black wash, or the
        // park green that painted cities as lawn.
        val landuse = assertNotNull(VectorStyle().paint("landuse", null))
        assertNull(landuse.fill)
        assertNull(landuse.stroke)
        // A paint that draws nothing, not "no opinion": null would let the polygon fallback wash
        // it grey, which is the thing this is here to prevent.
        assertEquals(LayerStyle(), VectorStyle().paint("landuse", "military"))
    }

    @Test
    fun `an airport is drawn rather than smudged`() {
        val style = VectorStyle()
        val ground = assertNotNull(assertNotNull(style.paint("aeroway", null)).fill)
        val runway = assertNotNull(style.paint("aeroway", "runway"))
        val minorRoad = assertNotNull(style.paint("transportation", null))

        // `aeroway` was ranked in the paint order but never given a paint, so it fell to the
        // geometry fallbacks: a translucent black scratch across a translucent black smudge.
        assertNotNull(assertNotNull(style.paint("aeroway", "apron")).fill)

        // Sources disagree about runway geometry — a line from one, the polygon of the strip from
        // another — so a runway carries both and reads the same either way.
        assertNotNull(runway.fill, "a runway shipped as a polygon would draw nothing")
        assertNotNull(runway.stroke, "a runway shipped as a line would draw nothing")
        assertTrue(
            runway.strokeWidth.at(13) > minorRoad.strokeWidth.at(13),
            "a runway is narrower than a side street"
        )
        assertTrue(assertNotNull(runway.fill).brightness() < style.background.brightness())
        assertTrue(ground.brightness() < style.background.brightness())
    }

    @Test
    fun `a plaza is not fenced off behind a phantom street`() {
        val style = VectorStyle()

        // OpenMapTiles files pedestrian areas and station platforms in `transportation`, as
        // polygons. The renderer paints a polygon only from a paint that has a fill — so a road
        // paint, which has none, no longer traces their rings as if each were a street.
        assertNull(assertNotNull(style.paint("transportation", null)).fill)
        listOf("motorway", "primary", "minor", "service", "path", "track", "rail", "ferry").forEach { klass ->
            assertNull(
                assertNotNull(style.paint("transportation", klass)).fill,
                "$klass would paint a plaza's ring"
            )
        }
    }

    @Test
    fun `a building reads as a building rather than as a tint on the land`() {
        val style = VectorStyle()
        val building = assertNotNull(style.paint("building", null))
        val fill = assertNotNull(building.fill)
        val outline = assertNotNull(building.stroke, "buildings have no outline, so a block is one mass")

        // At z16 and in, buildings are the map. The fill this replaced sat 0.08 off the land it
        // stood on, which is the same near-invisible gap the road casings were pulled out of.
        val fillGap = style.background.brightness() - fill.brightness()
        assertTrue(fillGap > 0.06f, "a building is only $fillGap off the land it stands on")
        assertTrue(fillGap < 0.2f, "a building is $fillGap off the land — the block reads as a hole")

        // The outline is what separates one building from the one it abuts, so it has to be darker
        // than the fill by more than the fill is darker than the land.
        val outlineGap = style.background.brightness() - outline.brightness()
        assertTrue(outline.brightness() < fill.brightness(), "the outline is no darker than the fill")
        assertTrue(outlineGap > 0.15f, "the outline is only $outlineGap off the land")

        // Kept to a hairline: buildings arrive around z14 as specks, and an outline with weight at
        // that size is the only thing that would show.
        assertTrue(building.strokeWidth.at(17) <= 1.dp, "the outline is ${building.strokeWidth.at(17)} thick")
    }

    @Test
    fun `a parish line is not drawn like a national border`() {
        val style = VectorStyle()
        fun border(level: String) =
            assertNotNull(style.paint("boundary", level), "no paint for admin_level $level")

        // Every boundary in OpenMapTiles carries the same class and differs only by `admin_level`,
        // so all of them drew as one gray line — on top of everything else on the map.
        val country = border("2")
        val state = border("4")
        val county = border("6")
        assertTrue(country.strokeWidth.at(12) > state.strokeWidth.at(12))
        assertTrue(state.strokeWidth.at(12) > county.strokeWidth.at(12))
        assertTrue(assertNotNull(country.stroke).brightness() < assertNotNull(state.stroke).brightness())
        assertTrue(assertNotNull(state.stroke).brightness() < assertNotNull(county.stroke).brightness())

        assertEquals("admin_level", VectorStyle.CLASS_AWARE_LAYERS["boundary"])
        assertEquals("class", VectorStyle.CLASS_AWARE_LAYERS["transportation"])
    }

    @Test
    fun `municipal boundaries draw nothing rather than netting the map over`() {
        val style = VectorStyle()

        // These arrive at street zoom and there are enough of them to lay a gray mesh over the
        // town. Nothing at all, not a zero width — a zero-width stroke is a hairline in Skia, so
        // asking for one would draw exactly what it is meant to hide.
        listOf("7", "8", "9", "10").forEach { level ->
            val paint = assertNotNull(style.paint("boundary", level), "no paint for admin_level $level")
            assertNull(paint.stroke, "admin_level $level still draws a line")
            assertNull(paint.fill)
        }
        // A source that ships no admin_level at all still gets the line it always got.
        assertNotNull(assertNotNull(style.paint("boundary", null)).stroke)
    }

    @Test
    fun `lines end and bend round rather than square and spiked`() {
        val style = VectorStyle()

        // Compose strokes butt-capped and miter-joined by default. A butt cap leaves a square end
        // on every cul-de-sac and on every road that stops at the junction it meets — and on the
        // casing too, so the dark edge ends in a flat wall across the road. A miter join spikes a
        // hairpin bend out to four times the width of the road it belongs to.
        val painted = listOf("transportation", "transportation_casing", "waterway", "boundary")
            .map { it to assertNotNull(style.paint(it, null), "no paint for $it") } +
            listOf("motorway", "minor", "service", "path", "rail", "ferry")
                .map { it to assertNotNull(style.paint("transportation", it), "no paint for $it") }

        painted.forEach { (name, paint) ->
            assertEquals(StrokeCap.Round, paint.cap, "$name ends square")
            assertEquals(StrokeJoin.Round, paint.join, "$name bends to a spike")
        }
        // And anything added to the style later starts round rather than opting in.
        assertEquals(StrokeCap.Round, LayerStyle().cap)
        assertEquals(StrokeJoin.Round, LayerStyle().join)
    }

    @Test
    fun `two styles built the same way are equal so a cache keyed on one survives`() {
        // A VectorPathTileLayer keys its decoded-tile cache on the style it was handed, because the
        // paint is baked into the cached paths. Value equality is what lets a caller build the
        // style where it needs it instead of having to reach for one shared instance.
        assertEquals(VectorStyle(), VectorStyle())

        // And the trap that goes with it: a lookup written as a lambda literal is a fresh object
        // every time it is evaluated, so a style built that way inside a composition compares
        // unequal on every frame and throws the whole decoded cache away.
        assertNotEquals(VectorStyle(), VectorStyle(road = { null }))
    }

    @Test
    fun `buildings can be left unoutlined and nothing else changes`() {
        val style = VectorStyle()
        val outlined = assertNotNull(style.building)
        val plain = style.copy(building = outlined.copy(stroke = null))

        // No paint at all, rather than a zero width: buildRenderTile builds a stroke op only for a
        // paint that has a stroke, so dropping the colour is what actually stops the geometry from
        // being traced — which is the entire point of being able to turn it off.
        assertNotNull(outlined.stroke)
        assertNull(assertNotNull(plain.paint("building", null)).stroke)
        // Same building otherwise: the fill is what says "this is built up", and the outline is
        // what is being dropped, not the block.
        assertEquals(outlined.fill, assertNotNull(plain.paint("building", null)).fill)

        // Everything else on the map is untouched — this is one paint, not a second style.
        assertEquals(
            style.paint("transportation", "motorway"),
            plain.paint("transportation", "motorway")
        )
    }

    @Test
    fun `a side street keeps its casing until it is a street you follow`() {
        val style = VectorStyle()
        val throughRoad = assertNotNull(style.paint("transportation", "primary"))
        val sideStreet = assertNotNull(style.paint("transportation", "residential"))
        val service = assertNotNull(style.paint("transportation", "service"))

        // A casing is the same geometry traced a second time, and the side streets are most of the
        // geometry — so they are the classes where the doubling is worth deferring.
        assertTrue(
            sideStreet.casingMinZoom > throughRoad.casingMinZoom,
            "a side street is cased from z${sideStreet.casingMinZoom}, no later than a primary road"
        )
        assertEquals(sideStreet.casingMinZoom, service.casingMinZoom)

        // And a service road is painted lighter than a street at both ends of that gate: it is the
        // colour of its edge above, and the whole of the line below.
        assertNotEquals(sideStreet.casing, service.casing)
        val edge = assertNotNull(service.casing)
        val street = assertNotNull(sideStreet.casing)
        assertTrue(
            edge.red > street.red && edge.green > street.green && edge.blue > street.blue,
            "a service road's edge is no lighter than a street's"
        )

        // And the line each collapses to below that gate is lighter still than its own edge. Two
        // thirds of the width it replaces was the white of the road, so a collapsed line painted in
        // the edge's colour makes the street darken as you zoom out and flash white as you zoom in.
        listOf("a side street" to sideStreet, "a service road" to service).forEach { (what, paint) ->
            val casing = assertNotNull(paint.casing)
            val coarse = assertNotNull(paint.coarse, "$what has no colour of its own to collapse to")
            assertTrue(
                coarse.red > casing.red && coarse.green > casing.green && coarse.blue > casing.blue,
                "$what collapses to $coarse, no lighter than the $casing edge it stands in for"
            )
        }

        // A road that keeps its casing all the way out has nothing to collapse and says nothing.
        assertNull(throughRoad.coarse)

        // Deferred, not dropped: at the zoom where you follow a street rather than see the pattern
        // of them, it is a white road with an edge like any other.
        assertEquals(throughRoad.casing, sideStreet.casing)
        assertEquals(throughRoad.stroke, sideStreet.stroke)
        assertTrue(sideStreet.casingMinZoom <= 15, "a side street is uncased as far in as z${sideStreet.casingMinZoom}")
    }

    @Test
    fun `the building outline is gated above the zoom where buildings are specks`() {
        val building = assertNotNull(VectorStyle().paint("building", null))

        // Tracing every ring on the tile costs the same whatever width comes out of it, so the
        // outline is gated off entirely rather than tapered away by ZoomWidth.
        assertTrue(building.strokeMinZoom > 0, "the building outline is expected to be zoom-gated")
        assertEquals(0, assertNotNull(VectorStyle().paint("transportation", null)).strokeMinZoom)
    }

    @Test
    fun `a class the table doesn't name still takes the layer's own paint`() {
        // `raceway` and whatever else a source invents fall through to the side-street paint rather
        // than to the geometry fallback, which would draw them as a translucent black scratch.
        val style = VectorStyle()
        val minor = assertNotNull(style.paint("transportation", null))
        assertEquals(minor, style.paint("transportation", "raceway"))
        // A layer with no classes ignores the one it is handed.
        assertEquals(style.water, style.paint("water", "ferry"))
        // And a layer the style says nothing about is left to the geometry fallbacks.
        assertNull(style.paint("wibble", null))
    }
}

/** Whether this colour reads as a green — enough to say "this is not painted as vegetation". */
private val Color.isGreen: Boolean get() = green > red && green > blue

/** Rough perceived brightness, enough to say "this is darker than that". */
private fun Color.brightness(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
