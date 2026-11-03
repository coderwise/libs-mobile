package com.coderwise.libs.mapview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a layer's content is told about being scaled up. The tile sizes behind the numbers are
 * [tileMagnification]'s, tested on their own; this is that they reach the content, and follow the
 * camera.
 */
@OptIn(ExperimentalTestApi::class)
class TileMagnificationTest {

    @Test
    fun `outside a layer nothing is scaled`() = runComposeUiTest {
        var magnification = 0f
        setContent { magnification = LocalTileMagnification.current() }
        assertEquals(1f, magnification)
    }

    @Test
    fun `a layer scaled up says so, and follows the zoom back down`() = runComposeUiTest {
        // A source that stops at z14, seen at 20: its tiles are 2^6 * 256 px apart on a 1x screen,
        // four times what a tile is measured at.
        val state = MapState<Unit>(zoomRange = 0..14)
        val camera = MapCameraState(zoom = 20f)
        val seen = mutableListOf<Float>()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MapView(camera, Modifier.size(400.dp)) {
                    layer(state) {
                        val magnification = LocalTileMagnification.current
                        Box(Modifier.fillMaxSize().drawBehind { seen += magnification() })
                    }
                }
            }
        }
        waitForIdle()

        assertTrue(seen.isNotEmpty(), "the layer was not drawn")
        assertEquals(setOf(4f), seen.toSet())

        // Three levels past the source is 2048 px a tile, which is measured as it is.
        runOnIdle { camera.moveTo(camera.center, 17f) }
        waitForIdle()
        assertEquals(1f, seen.last())
    }
}
