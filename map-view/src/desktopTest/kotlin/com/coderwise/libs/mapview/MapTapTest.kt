package com.coderwise.libs.mapview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [mapTap] and the line that is built on it, driven by real pointers on a 400 px map. Every shape
 * here is a point with a reach of 24 px either side, which is all a distance function has to say.
 */
@OptIn(ExperimentalTestApi::class)
class MapTapTest {

    private val camera = { MapCameraState(zoom = 12f) }

    @Test
    fun `a tap within reach of a shape is a tap on it`() {
        val taps = mutableListOf<String>()
        map(camera()) { shape("a", Offset(100f, 100f), taps) }.tap(Offset(110f, 100f))
        assertEquals(listOf("a"), taps)
    }

    @Test
    fun `a tap beyond reach is not`() {
        val taps = mutableListOf<String>()
        map(camera()) { shape("a", Offset(100f, 100f), taps) }.tap(Offset(200f, 200f))
        assertEquals(emptyList(), taps)
    }

    @Test
    fun `the nearest shape wins the tap, not the one declared last`() {
        val taps = mutableListOf<String>()
        val shapes: @Composable MapOverlayScope.() -> Unit = {
            shape("near", Offset(100f, 100f), taps)
            shape("top", Offset(112f, 100f), taps)
        }
        map(camera(), overlay = shapes).tap(Offset(102f, 100f))
        assertEquals(listOf("near"), taps)
    }

    @Test
    fun `and the other way round when the touch is nearer the other`() {
        val taps = mutableListOf<String>()
        val shapes: @Composable MapOverlayScope.() -> Unit = {
            shape("near", Offset(100f, 100f), taps)
            shape("top", Offset(112f, 100f), taps)
        }
        map(camera(), overlay = shapes).tap(Offset(110f, 100f))
        assertEquals(listOf("top"), taps)
    }

    @Test
    fun `a shape that is missed leaves the tap to the map`() {
        val taps = mutableListOf<String>()
        val onMap = mutableListOf<LatLon>()
        map(camera(), onTap = { onMap += it }) { shape("a", Offset(100f, 100f), taps) }.tap(Offset(300f, 300f))
        assertEquals(emptyList(), taps)
        assertEquals(1, onMap.size, "the map did not hear the tap")
    }

    @Test
    fun `a shape that is hit keeps the tap from the map`() {
        val taps = mutableListOf<String>()
        val onMap = mutableListOf<LatLon>()
        map(camera(), onTap = { onMap += it }) { shape("a", Offset(100f, 100f), taps) }.tap(Offset(100f, 100f))
        assertEquals(listOf("a"), taps)
        assertEquals(emptyList(), onMap)
    }

    @Test
    fun `a drag that starts on a shape pans the map instead`() {
        val taps = mutableListOf<String>()
        val camera = camera()
        val before = camera.center
        map(camera) { shape("a", Offset(100f, 100f), taps) }.act {
            down(Offset(100f, 100f))
            repeat(6) { step -> moveTo(Offset(100f + (step + 1) * 20f, 100f), delayMillis = 100) }
            up()
        }
        assertEquals(emptyList(), taps)
        assertTrue(camera.center.lon < before.lon, "the map did not pan: $before to ${camera.center}")
    }

    @Test
    fun `a polyline is tapped along its length and not off it`() {
        val taps = mutableListOf<String>()
        // A line through the middle of the map, a degree either side of it.
        val line = listOf(LatLon(0.0, -0.01), LatLon(0.0, 0.01))
        val map = map(camera()) { Polyline(line, Color.Blue, onClick = { taps += "line" }) }

        map.tap(Offset(200f, 205f))
        assertEquals(listOf("line"), taps)
        map.tap(Offset(200f, 300f))
        assertEquals(listOf("line"), taps, "a tap 100 px off the line was heard")
    }

    @Test
    fun `a polyline that is nearer than another takes the tap from it`() {
        val taps = mutableListOf<String>()
        val shapes: @Composable MapOverlayScope.() -> Unit = {
            Polyline(
                listOf(LatLon(0.0, -0.01), LatLon(0.0, 0.01)), Color.Blue,
                onClick = { taps += "line" }
            )
            shape("point", Offset(200f, 215f), taps)
        }
        // 3 px from the line and 12 from the point, which is declared after it and so is on top.
        map(camera(), overlay = shapes).tap(Offset(200f, 203f))
        assertEquals(listOf("line"), taps)
    }
}

@Composable
private fun MapOverlayScope.shape(name: String, at: Offset, taps: MutableList<String>) {
    Box(
        Modifier.fillMaxSize().mapTap(
            distance = { (it - at).getDistance() },
            touchWidth = 48.dp,
            onClick = { taps += name }
        )
    )
}

@OptIn(ExperimentalTestApi::class)
private fun map(
    camera: MapCameraState,
    onTap: ((LatLon) -> Unit)? = null,
    overlay: @Composable MapOverlayScope.() -> Unit
) = TestMap(camera, onTap, overlay)

/**
 * A map to be touched. It is composed afresh for every gesture, so one map can be tapped several
 * times while the shapes it hears from stay the test's own.
 */
@OptIn(ExperimentalTestApi::class)
private class TestMap(
    private val camera: MapCameraState,
    private val onTap: ((LatLon) -> Unit)?,
    private val overlay: @Composable MapOverlayScope.() -> Unit
) {
    fun tap(at: Offset) = act { click(at) }

    fun act(input: TouchInjectionScope.() -> Unit) = runComposeUiTest {
        setContent {
            // Pixels are dp here, so a position in a test is where it lands on screen.
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(400.dp).testTag("map").mapGestures(camera, onTap = onTap)) {
                    MapView(camera, Modifier.fillMaxSize()) {
                        overlay { overlay() }
                    }
                }
            }
        }
        onNodeWithTag("map").performTouchInput(input)
        // The tap waits out the double-tap window before it is one.
        mainClock.advanceTimeBy(1000)
        waitForIdle()
    }
}
