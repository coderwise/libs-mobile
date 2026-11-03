package com.coderwise.libs.mapview.geojson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.MapCameraState
import com.coderwise.libs.mapview.MapOverlayScope
import com.coderwise.libs.mapview.MapView
import com.coderwise.libs.mapview.mapGestures

/**
 * A 400 px map to be touched and looked at, with a white ground and the camera in the middle. At
 * zoom 12 a degree is 2913 px at the equator, so a hundredth of one is about 29 px.
 *
 * It is composed afresh for every gesture and every look, so one map can be tapped several times
 * while the shapes it hears from stay the test's own.
 */
@OptIn(ExperimentalTestApi::class)
internal class TestMap(
    private val camera: MapCameraState,
    private val onTap: ((LatLon) -> Unit)? = null,
    private val overlay: @Composable MapOverlayScope.() -> Unit
) {
    fun tap(x: Float, y: Float) = act { click(Offset(x, y)) }

    fun act(input: TouchInjectionScope.() -> Unit) = runComposeUiTest {
        setContent { Content() }
        onNodeWithTag("map").performTouchInput(input)
        // The tap waits out the double-tap window before it is one.
        mainClock.advanceTimeBy(1000)
        waitForIdle()
    }

    /** What the map looks like. */
    fun look(): Look {
        lateinit var look: Look
        runComposeUiTest {
            setContent { Content() }
            waitForIdle()
            look = Look(onNodeWithTag("map").captureToImage().toPixelMap().let { map -> { x, y -> map[x, y] } })
        }
        return look
    }

    @Composable
    private fun Content() {
        // Pixels are dp here, so a position in a test is where it lands on screen.
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            Box(Modifier.size(400.dp).testTag("map").background(Color.White).mapGestures(camera, onTap = onTap)) {
                MapView(camera, Modifier.fillMaxSize()) {
                    overlay { overlay() }
                }
            }
        }
    }
}

internal class Look(private val pixel: (Int, Int) -> Color) {
    operator fun get(x: Int, y: Int) = pixel(x, y)
}
