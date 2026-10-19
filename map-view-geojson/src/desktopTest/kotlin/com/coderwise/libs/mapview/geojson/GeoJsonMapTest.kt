package com.coderwise.libs.mapview.geojson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.MapCameraState
import com.coderwise.libs.mapview.MapView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [GeoJson] on a real [MapView]: what it draws through the map's own projection, and where the
 * map's own taps go. The camera is at 0, 0 and zoom 12 unless a test says, so a hundredth of a
 * degree is about 29 px from the middle, which is at 200, 200.
 */
@OptIn(ExperimentalTestApi::class)
class GeoJsonMapTest {

    private val red = GeoJsonStyle(fill = Color.Red, stroke = Color.Transparent)

    private fun camera() = MapCameraState(zoom = 12f)

    /** Degrees to pixels, at zoom 12 near the equator. */
    private fun px(degrees: Double) = (degrees * 1_048_576.0 / 360.0).toFloat()

    // The box is 0.02 degrees either side of the middle: 58 px, from 142 to 258.
    private val box = polygon(square(0.0, 0.0, 0.02))

    // ---- drawing

    @Test
    fun `a polygon is drawn where the map has it`() {
        val look = TestMap(camera()) { GeoJson(collection(feature(box)), style = red) }.look()
        assertEquals(Color.Red, look[200, 200])
        assertEquals(Color.Red, look[150, 150])
        assertEquals(Color.White, look[100, 200])
        assertEquals(Color.White, look[300, 200])
    }

    @Test
    fun `a hole is a hole on the map`() {
        val withHole = polygon(square(0.0, 0.0, 0.02), square(0.0, 0.0, 0.01))
        val look = TestMap(camera()) { GeoJson(collection(feature(withHole)), style = red) }.look()
        assertEquals(Color.White, look[200, 200], "the hole")
        assertEquals(Color.Red, look[245, 200], "between the hole and the outside")
        assertEquals(Color.Red, look[200, 155])
    }

    @Test
    fun `a polygon across the antimeridian is one polygon`() {
        // From 179.98 to -179.98: 58 px wide, with the camera on the line.
        val across = polygon(ring(179.98, -0.02, 180.02, 0.02).map { if (it.lon > 180) it.copy(lon = it.lon - 360) else it })
        val look = TestMap(MapCameraState(LatLon(0.0, 180.0), 12f)) {
            GeoJson(collection(feature(across)), style = red)
        }.look()
        assertEquals(Color.Red, look[180, 200], "west of the line")
        assertEquals(Color.Red, look[220, 200], "east of it")
        assertEquals(Color.White, look[100, 200], "and not the way round the world")
        assertEquals(Color.White, look[300, 200])
    }

    @Test
    fun `a layer is drawn over the one before it`() {
        val over = polygon(ring(0.0, -0.02, 0.04, 0.02))
        val look = TestMap(camera()) {
            GeoJson(collection(feature(box)), style = red)
            GeoJson(collection(feature(over)), style = GeoJsonStyle(fill = Color.Blue, stroke = Color.Transparent))
        }.look()
        assertEquals(Color.Red, look[160, 200], "only in the first")
        assertEquals(Color.Blue, look[220, 200], "in both")
    }

    @Test
    fun `a layer appears at its minimum zoom`() {
        val camera = MapCameraState(zoom = 11f)
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.size(400.dp).testTag("map").background(Color.White)) {
                        MapView(camera, Modifier.fillMaxSize()) {
                            overlay { GeoJson(collection(feature(polygon(square(0.0, 0.0, 0.04)))), style = red, minZoom = 12f) }
                        }
                    }
                }
            }
            fun middle() = onNodeWithTag("map").captureToImage().toPixelMap()[200, 200]

            waitForIdle()
            assertEquals(Color.White, middle(), "zoomed out past it")
            camera.moveTo(LatLon(0.0, 0.0), 12f)
            waitForIdle()
            assertEquals(Color.Red, middle(), "at it")
            camera.moveTo(LatLon(0.0, 0.0), 11.5f)
            waitForIdle()
            assertEquals(Color.White, middle(), "and gone again")
        }
    }

    @Test
    fun `a point is a dot on the map`() {
        val look = TestMap(camera()) {
            GeoJson(collection(feature(Geometry.Point(LatLon(0.0, 0.0)))), style = GeoJsonStyle(pointColor = Color.Green))
        }.look()
        assertEquals(Color.Green, look[200, 200])
        assertEquals(Color.White, look[212, 200])
    }

    // ---- taps

    private class Taps {
        val features = mutableListOf<String?>()
        val map = mutableListOf<LatLon>()
    }

    private fun tapped(
        x: Float,
        y: Float,
        camera: MapCameraState = camera(),
        overlay: @androidx.compose.runtime.Composable com.coderwise.libs.mapview.MapOverlayScope.(Taps) -> Unit
    ): Taps {
        val taps = Taps()
        TestMap(camera, onTap = { taps.map += it }) { overlay(taps) }.tap(x, y)
        return taps
    }

    @Test
    fun `a tap inside a polygon is a tap on its feature`() {
        val taps = tapped(200f, 200f) { taps ->
            GeoJson(collection(feature(box, "box")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("box"), taps.features)
    }

    @Test
    fun `and the map does not hear it`() {
        val taps = tapped(200f, 200f) { taps ->
            GeoJson(collection(feature(box, "box")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(emptyList(), taps.map)
    }

    @Test
    fun `a tap in a hole is a tap on the map`() {
        val holed = polygon(square(0.0, 0.0, 0.02), square(0.0, 0.0, 0.01))
        val taps = tapped(200f, 200f) { taps ->
            GeoJson(collection(feature(holed, "ring")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(emptyList(), taps.features)
        assertEquals(1, taps.map.size, "the map did not hear the tap")
    }

    @Test
    fun `a tap on the ring round a hole is a tap on the polygon`() {
        val holed = polygon(square(0.0, 0.0, 0.02), square(0.0, 0.0, 0.01))
        val taps = tapped(245f, 200f) { taps ->
            GeoJson(collection(feature(holed, "ring")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("ring"), taps.features)
    }

    @Test
    fun `a tap just outside a polygon is a tap on it and a tap well outside is not`() {
        fun at(x: Float) = tapped(x, 200f) { taps ->
            GeoJson(collection(feature(box, "box")), onFeatureClick = { taps.features += it.id })
        }
        // The edge is at 258 and a touch reaches 12 px.
        assertEquals(listOf<String?>("box"), at(266f).features)
        assertEquals(emptyList(), at(278f).features)
        assertEquals(1, at(278f).map.size, "which is the map's")
    }

    @Test
    fun `a tap gets the feature it landed on`() {
        val west = polygon(ring(-0.04, -0.01, -0.01, 0.01))
        val east = polygon(ring(0.01, -0.01, 0.04, 0.01))
        fun at(x: Float) = tapped(x, 200f) { taps ->
            GeoJson(collection(feature(west, "west"), feature(east, "east")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("west"), at(150f).features)
        assertEquals(listOf<String?>("east"), at(250f).features)
    }

    @Test
    fun `a line is tapped along its length and not off it`() {
        val line = Geometry.LineString(listOf(LatLon(0.0, -0.02), LatLon(0.0, 0.02)))
        fun at(x: Float, y: Float) = tapped(x, y) { taps ->
            GeoJson(collection(feature(line, "line")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("line"), at(200f, 205f).features)
        assertEquals(emptyList(), at(200f, 300f).features)
        assertEquals(emptyList(), at(300f, 200f).features, "beyond its end")
    }

    @Test
    fun `a point is tapped within reach of it`() {
        val point = Geometry.Point(LatLon(0.0, 0.0))
        fun at(x: Float) = tapped(x, 200f) { taps ->
            GeoJson(collection(feature(point, "dot")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("dot"), at(208f).features)
        assertEquals(emptyList(), at(230f).features)
    }

    @Test
    fun `the nearest of two overlapping shapes wins, not the one on top`() {
        // The first covers 142 to 258 and the second, declared after it and so drawn over it,
        // 229 to 287.
        val under = box
        val over = polygon(ring(0.01, -0.02, 0.03, 0.02))
        fun at(x: Float) = tapped(x, 200f) { taps ->
            GeoJson(collection(feature(under, "under")), onFeatureClick = { taps.features += it.id })
            GeoJson(collection(feature(over, "over")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("under"), at(220f).features, "inside the first and 9 px from the second")
        assertEquals(listOf<String?>("over"), at(280f).features, "inside the second alone")
        assertEquals(listOf<String?>("over"), at(265f).features, "inside the second and 7 px from the first")
    }

    @Test
    fun `and so does a shape of another kind`() {
        val line = Geometry.LineString(listOf(LatLon(0.0, -0.04), LatLon(0.0, 0.04)))
        val taps = tapped(200f, 204f) { taps ->
            GeoJson(collection(feature(line, "line")), onFeatureClick = { taps.features += it.id })
            // Declared last, so on top, and 4 px from the line is not as near as inside it.
            GeoJson(collection(feature(box, "box")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("box"), taps.features)
    }

    @Test
    fun `inside several polygons of a layer the one drawn on top is the tap`() {
        val taps = tapped(200f, 200f) { taps ->
            GeoJson(
                collection(feature(box, "under"), feature(polygon(square(0.0, 0.0, 0.01)), "over")),
                onFeatureClick = { taps.features += it.id }
            )
        }
        assertEquals(listOf<String?>("over"), taps.features)
    }

    @Test
    fun `a layer with no click listener leaves the tap to the map`() {
        val taps = tapped(200f, 200f) { GeoJson(collection(feature(box, "box"))) }
        assertEquals(1, taps.map.size)
    }

    @Test
    fun `a layer under its minimum zoom cannot be tapped`() {
        fun at(minZoom: Float) = tapped(200f, 200f) { taps ->
            GeoJson(collection(feature(box, "box")), minZoom = minZoom, onFeatureClick = { taps.features += it.id })
        }
        assertEquals(emptyList(), at(12.5f).features)
        assertEquals(1, at(12.5f).map.size, "the map has it instead")
        assertEquals(listOf<String?>("box"), at(12f).features)
    }

    @Test
    fun `a turned map is tapped where the shape is drawn`() {
        // East of the middle, with east put at the top: the box is 29 to 87 px above the middle.
        val east = polygon(ring(0.01, -0.01, 0.03, 0.01))
        fun at(x: Float, y: Float) = tapped(x, y, MapCameraState(zoom = 12f, bearing = 90f)) { taps ->
            GeoJson(collection(feature(east, "east")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("east"), at(200f, 142f).features, "where it is")
        assertEquals(emptyList(), at(258f, 200f).features, "and not where it would be if north were up")
    }

    @Test
    fun `a map zoomed further in measures a tap in the pixels it has`() {
        // A degree is four times the pixels at zoom 14, so the box is 4 times as wide and the
        // 12 px reach is the same 12.
        fun at(x: Float) = tapped(x, 200f, MapCameraState(zoom = 14f)) { taps ->
            GeoJson(collection(feature(polygon(square(0.0, 0.0, 0.005)), "box")), onFeatureClick = { taps.features += it.id })
        }
        // 0.005 degrees is 58 px at zoom 14, edge at 258.
        assertEquals(listOf<String?>("box"), at(266f).features)
        assertEquals(emptyList(), at(278f).features)
    }

    @Test
    fun `a polygon across the antimeridian is tapped on both sides of it`() {
        val across = polygon(ring(179.98, -0.02, 180.02, 0.02).map { if (it.lon > 180) it.copy(lon = it.lon - 360) else it })
        fun at(x: Float) = tapped(x, 200f, MapCameraState(LatLon(0.0, 180.0), 12f)) { taps ->
            GeoJson(collection(feature(across, "across")), onFeatureClick = { taps.features += it.id })
        }
        assertEquals(listOf<String?>("across"), at(180f).features)
        assertEquals(listOf<String?>("across"), at(220f).features)
        assertEquals(emptyList(), at(100f).features)
    }

    @Test
    fun `a drag that starts on a shape pans the map instead`() {
        val taps = Taps()
        val camera = camera()
        val before = camera.center
        TestMap(camera) {
            GeoJson(collection(feature(box, "box")), onFeatureClick = { taps.features += it.id })
        }.act {
            down(Offset(200f, 200f))
            repeat(6) { step -> moveTo(Offset(200f + (step + 1) * 20f, 200f), delayMillis = 100) }
            up()
        }
        assertEquals(emptyList(), taps.features)
        assertTrue(camera.center.lon < before.lon, "the map did not pan: $before to ${camera.center}")
    }
}
