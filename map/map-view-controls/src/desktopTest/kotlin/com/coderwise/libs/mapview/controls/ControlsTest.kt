package com.coderwise.libs.mapview.controls

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.MapCameraState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The controls pressed for real, on a camera of their own. */
@OptIn(ExperimentalTestApi::class)
class ControlsTest {

    // --- zoom buttons --------------------------------------------------------------------------

    @Test
    fun `zoom in goes in a level`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f)
        setContent { ZoomButtons(camera) }
        onNodeWithContentDescription("Zoom in").performClick()
        waitForIdle()
        assertEquals(6f, camera.zoom, 1e-3f)
    }

    @Test
    fun `zoom out comes out a level, and keeps where the map is looking`() = runComposeUiTest {
        val camera = MapCameraState(LatLon(51.5, -0.12), zoom = 8f)
        setContent { ZoomButtons(camera) }
        onNodeWithContentDescription("Zoom out").performClick()
        waitForIdle()
        assertEquals(7f, camera.zoom, 1e-3f)
        assertEquals(51.5, camera.center.lat, 1e-6)
        assertEquals(-0.12, camera.center.lon, 1e-6)
    }

    @Test
    fun `a press at the end of the range is turned off`() = runComposeUiTest {
        val nearest = MapCameraState(zoom = 20f)
        setContent { ZoomButtons(nearest) }
        onNodeWithContentDescription("Zoom in").assertIsNotEnabled()
        onNodeWithContentDescription("Zoom out").assertIsEnabled()
    }

    @Test
    fun `the range is the caller's to narrow`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 14f)
        setContent { ZoomButtons(camera, zoomRange = 1f..14f) }
        onNodeWithContentDescription("Zoom in").assertIsNotEnabled()
        onNodeWithContentDescription("Zoom out").performClick()
        waitForIdle()
        assertEquals(13f, camera.zoom, 1e-3f)
    }

    // --- north button --------------------------------------------------------------------------

    @Test
    fun `a map facing north has no north button`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f)
        setContent { NorthButton(camera) }
        onNodeWithContentDescription("North").assertDoesNotExist()
    }

    @Test
    fun `a turned map has one, and it turns the map back`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f, bearing = 90f)
        setContent { NorthButton(camera) }
        onNodeWithContentDescription("North").performClick()
        waitForIdle()
        assertEquals(0f, camera.bearing)
        onNodeWithContentDescription("North").assertDoesNotExist()
    }

    @Test
    fun `it can be kept on screen`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f)
        setContent { NorthButton(camera, alwaysVisible = true) }
        onNodeWithContentDescription("North").assertIsEnabled()
    }

    @Test
    fun `the map is turned the short way round to north`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f, bearing = 350f)
        val seen = mutableListOf<Float>()
        setContent {
            NorthButton(camera, alwaysVisible = true)
            LaunchedEffect(Unit) { snapshotFlow { camera.bearing }.collect { seen += it } }
        }
        onNodeWithContentDescription("North").performClick()
        waitForIdle()

        assertEquals(0f, camera.bearing)
        // Ten degrees past 350, never the three hundred and forty back through the south.
        assertTrue(seen.all { it >= 350f || it <= 0.001f }, "went the long way: $seen")
    }

    // --- course-up -----------------------------------------------------------------------------

    @Test
    fun `course-up turns the map to the heading`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f)
        var heading by mutableStateOf<Float?>(null)
        setContent { CourseUp(camera, heading) }

        waitForIdle()
        assertEquals(0f, camera.bearing)

        heading = 90f
        waitForIdle()
        assertEquals(90f, camera.bearing)
    }

    @Test
    fun `course-up goes the short way round`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f, bearing = 350f)
        val seen = mutableListOf<Float>()
        setContent {
            CourseUp(camera, heading = 10f)
            LaunchedEffect(Unit) { snapshotFlow { camera.bearing }.collect { seen += it } }
        }
        waitForIdle()

        assertEquals(10f, camera.bearing)
        assertTrue(seen.none { it in 11f..349f }, "went the long way: $seen")
    }

    @Test
    fun `a heading that wobbles by a degree does not move the map`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f, bearing = 90f)
        var heading by mutableStateOf<Float?>(91f)
        setContent { CourseUp(camera, heading) }
        waitForIdle()
        assertEquals(90f, camera.bearing)

        heading = 93f // past the deadband
        waitForIdle()
        assertEquals(93f, camera.bearing)
    }

    @Test
    fun `course-up that is off leaves the map alone`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f, bearing = 0f)
        var enabled by mutableStateOf(false)
        setContent { CourseUp(camera, heading = 120f, enabled = enabled) }
        waitForIdle()
        assertEquals(0f, camera.bearing)

        enabled = true
        waitForIdle()
        assertEquals(120f, camera.bearing)
    }

    // --- scale bar, tape and credit ------------------------------------------------------------

    @Test
    fun `the scale bar says what its bar is`() = runComposeUiTest {
        // Zoom 15 on the equator is 4.78 m a dp: 573 m of room for a bar of 500.
        val camera = MapCameraState(LatLon(0.0, 0.0), zoom = 15f)
        setContent { ScaleBar(camera) }
        onNodeWithText("500 m").assertExists()
    }

    @Test
    fun `and says it again in feet and miles when asked`() = runComposeUiTest {
        val camera = MapCameraState(LatLon(0.0, 0.0), zoom = 15f)
        setContent { ScaleBar(camera, units = ScaleUnits.Imperial) }
        onNodeWithText("1000 ft").assertExists()
    }

    @Test
    fun `the scale bar follows the zoom`() = runComposeUiTest {
        val camera = MapCameraState(LatLon(0.0, 0.0), zoom = 15f)
        setContent { ScaleBar(camera) }
        onNodeWithText("500 m").assertExists()

        // Two levels in is a quarter of the ground per dp: 143 m of room, a bar of 100.
        camera.moveTo(camera.center, zoom = 17f)
        waitForIdle()
        onNodeWithText("100 m").assertExists()
    }

    @Test
    fun `a credit shows its text, and can be pressed when it has somewhere to go`() = runComposeUiTest {
        var opened = 0
        setContent { Attribution("© OpenStreetMap contributors", onClick = { opened++ }) }
        onNodeWithText("© OpenStreetMap contributors").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the compass tape can be pressed`() = runComposeUiTest {
        val camera = MapCameraState(zoom = 5f, bearing = 45f)
        var pressed = 0
        setContent { CompassTape(camera, onClick = { pressed++ }) }
        onNodeWithContentDescription("Compass").performClick()
        assertEquals(1, pressed)
    }
}
