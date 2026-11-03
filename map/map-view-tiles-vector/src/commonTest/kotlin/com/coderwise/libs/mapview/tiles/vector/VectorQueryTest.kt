package com.coderwise.libs.mapview.tiles.vector

import com.coderwise.libs.mapview.tiles.vector.mvt.GeometryType
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtFeature
import com.coderwise.libs.mapview.tiles.vector.mvt.MvtLayer
import com.coderwise.libs.mapview.tiles.vector.mvt.TileGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorQueryTest {

    @Test
    fun `an area answers for the points inside it and not for the ones outside`() {
        val tile = listOf(layer("landcover", polygon("wood", 100, 100, 900, 900)))

        val inside = featuresIn(tile, at(500), at(500), RADIUS)
        val outside = featuresIn(tile, at(950), at(950), RADIUS)

        assertEquals("wood", inside.single().featureClass)
        assertEquals(MapFeatureKind.LAND, inside.single().kind)
        assertEquals(0f, inside.single().distance)
        assertTrue(outside.isEmpty(), "a wood 50 units away is not what is here: $outside")
    }

    @Test
    fun `a hole in an area is not inside it`() {
        // One feature, two rings: a square with a square bitten out of the middle of it.
        val geometry = TileGeometry(
            coords = ring(100, 100, 900, 900) + ring(400, 400, 600, 600),
            partStarts = intArrayOf(0, 5)
        )
        val tile = listOf(
            MvtLayer(
                name = "water",
                extent = EXTENT,
                features = listOf(MvtFeature(GeometryType.POLYGON, geometry, featureClass = "lake"))
            )
        )

        assertEquals("lake", featuresIn(tile, at(200), at(200), RADIUS).single().featureClass)
        assertTrue(featuresIn(tile, at(500), at(500), RADIUS).isEmpty())
    }

    @Test
    fun `a line answers within the press radius and not beyond it`() {
        val tile = listOf(layer("transportation", line("track", 0, 500, 1000, 500)))

        val onIt = featuresIn(tile, at(500), at(500), RADIUS)
        val wellOff = featuresIn(tile, at(500), at(900), RADIUS)

        assertEquals("track", onIt.single().featureClass)
        assertTrue(wellOff.isEmpty(), "a track 400 units away should not answer: $wellOff")
    }

    @Test
    fun `a named road beats the unnamed line carrying the same road`() {
        // The OpenMapTiles split: `transportation` has every road, `transportation_name` the names.
        val tile = listOf(
            layer("transportation", line("path", 0, 500, 1000, 500)),
            layer("transportation_name", line("path", 0, 500, 1000, 500, name = "Laugavegur"))
        )

        val found = featuresIn(tile, at(500), at(500), RADIUS)

        assertEquals(1, found.size, "the two halves of one road are one answer: $found")
        assertEquals("Laugavegur", found.single().name)
        assertEquals("path", found.single().featureClass)
    }

    @Test
    fun `the nearer of two roads is the one pressed`() {
        val tile = listOf(
            layer(
                "transportation_name",
                line("primary", 0, 495, 1000, 495, name = "near"),
                line("primary", 0, 510, 1000, 510, name = "far")
            )
        )

        assertEquals("near", featuresIn(tile, at(500), at(500), RADIUS).single().name)
    }

    @Test
    fun `answers come back most specific first`() {
        val tile = listOf(
            layer("landcover", polygon("wood", 0, 0, 1000, 1000)),
            layer("transportation", line("path", 0, 500, 1000, 500)),
            layer("poi", point("cafe", 500, 500, name = "Kaffi"))
        )

        val found = featuresIn(tile, at(500), at(500), RADIUS)

        assertEquals(
            listOf(MapFeatureKind.POI, MapFeatureKind.ROAD, MapFeatureKind.LAND),
            found.map { it.kind }
        )
        assertEquals("Kaffi", found.first().name)
    }

    @Test
    fun `a settlement point is not treated as the ground under the press`() {
        val tile = listOf(layer("place", point("village", 500, 500, name = "Hvolsvöllur")))

        assertTrue(featuresIn(tile, at(500), at(500), RADIUS).isEmpty())
    }

    @Test
    fun `a feature with neither a name nor a class says nothing`() {
        val tile = listOf(layer("building", polygon(featureClass = null, 100, 100, 900, 900)))

        assertTrue(featuresIn(tile, at(500), at(500), RADIUS).isEmpty())
    }

    @Test
    fun `a layer that declares its own extent is measured in that extent`() {
        // Half the extent, so the same fraction of the tile lands on half the unit coordinates.
        val tile = listOf(
            MvtLayer(
                name = "landcover",
                extent = EXTENT / 2,
                features = listOf(polygon("wood", 50, 50, 450, 450))
            )
        )

        assertEquals("wood", featuresIn(tile, at(500), at(500), RADIUS).single().featureClass)
        assertTrue(featuresIn(tile, at(950), at(950), RADIUS).isEmpty())
    }

    @Test
    fun `bad bytes are no answer rather than a crash`() {
        assertTrue(featuresAt(byteArrayOf(1, 2, 3, 4, 5), at(500), at(500), RADIUS).isEmpty())
    }

    /** A position given in the unit coordinates the fixtures use, as the fraction of a tile it is. */
    private fun at(unit: Int): Float = unit.toFloat() / EXTENT

    private fun layer(name: String, vararg features: MvtFeature) =
        MvtLayer(name, EXTENT, features.toList())

    private fun polygon(featureClass: String?, x1: Int, y1: Int, x2: Int, y2: Int) =
        MvtFeature(
            type = GeometryType.POLYGON,
            geometry = TileGeometry(ring(x1, y1, x2, y2), intArrayOf(0)),
            featureClass = featureClass
        )

    private fun line(featureClass: String?, x1: Int, y1: Int, x2: Int, y2: Int, name: String? = null) =
        MvtFeature(
            type = GeometryType.LINE,
            geometry = TileGeometry(intArrayOf(x1, y1, x2, y2), intArrayOf(0)),
            featureClass = featureClass,
            name = name
        )

    private fun point(featureClass: String?, x: Int, y: Int, name: String? = null) =
        MvtFeature(
            type = GeometryType.POINT,
            geometry = TileGeometry(intArrayOf(x, y), intArrayOf(0)),
            featureClass = featureClass,
            name = name
        )

    /** A closed axis-aligned rectangle, the way the decoder leaves one: first vertex repeated. */
    private fun ring(x1: Int, y1: Int, x2: Int, y2: Int) =
        intArrayOf(x1, y1, x2, y1, x2, y2, x1, y2, x1, y1)

    private companion object {
        const val EXTENT = 4096

        /** Wide enough to catch a road under a finger, tight enough to miss the next one over. */
        const val RADIUS = 25f / EXTENT
    }
}
