package com.coderwise.libs.mapview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The gestures driven by real pointers. The clock runs on its own here, so what a test sees is
 * where the map came to rest: the gesture itself plus whatever fling it earned.
 */
@OptIn(ExperimentalTestApi::class)
class MapGesturesTest {

    /**
     * A pinch leaves one finger still travelling when the other comes up, and that finger is not
     * making a throw — it is finishing a zoom. The same pinch is made twice, once with a quick
     * tail and once with a slow one: if the tail were flung the quick one would coast further,
     * which is the map sailing off the moment a zoom ends.
     */
    @Test
    fun `a pinch does not fling when one finger lifts before the other`() {
        fun pinch(tailDelay: Long): MapCameraState = gesture {
            down(0, Offset(190f, 200f))
            down(1, Offset(210f, 200f))
            repeat(10) { step ->
                // Both fingers in one event, the way a hand moves them.
                updatePointerTo(0, Offset(190f - (step + 1) * 15f, 200f))
                updatePointerTo(1, Offset(210f + (step + 1) * 15f, 200f))
                move()
            }
            up(1)
            // The finger left behind keeps going, as it does when one lets go first.
            repeat(4) { step -> moveTo(0, Offset(40f - (step + 1) * 15f, 200f), delayMillis = tailDelay) }
            up(0)
        }

        val quick = pinch(tailDelay = 16)
        val slow = pinch(tailDelay = 200)

        assertTrue(quick.zoom > START_ZOOM, "the pinch did not zoom: ${quick.zoom}")
        assertTrue(
            abs(quick.center.lon - slow.center.lon) < 1e-9,
            "the pinch tail was flung: ${quick.center.lon} vs ${slow.center.lon}"
        )
    }

    /**
     * A gesture that earns no fling still has to end. The map is the user's while a finger is on
     * it, and anything moving the camera on the app's behalf — following a GPS fix, most of all —
     * waits for that to be over. A pinch that never says it finished leaves the map held for
     * good, and the camera never moves itself again.
     */
    @Test
    fun `a pinch lets go of the map`() {
        val camera = gesture {
            down(0, Offset(190f, 200f))
            down(1, Offset(210f, 200f))
            repeat(10) { step ->
                updatePointerTo(0, Offset(190f - (step + 1) * 15f, 200f))
                updatePointerTo(1, Offset(210f + (step + 1) * 15f, 200f))
                move()
            }
            up(1)
            up(0)
        }

        assertTrue(camera.zoom > START_ZOOM, "the pinch did not zoom: ${camera.zoom}")
        assertFalse(camera.isInteracting, "the map was still held after the pinch")
    }

    /** And a plain drag, which has always ended, still ends. */
    @Test
    fun `a drag lets go of the map`() {
        val camera = gesture {
            down(0, Offset(300f, 200f))
            repeat(7) { step -> moveTo(0, Offset(300f - (step + 1) * 30f, 200f), delayMillis = 200) }
            up(0)
        }

        assertFalse(camera.isInteracting, "the map was still held after the drag")
    }

    /**
     * And the throw the rule must leave alone: the same drag hurried and dawdled, where the
     * hurried one has to end up further along.
     */
    @Test
    fun `a one-finger flick still flings`() {
        fun drag(delay: Long): MapCameraState = gesture {
            down(0, Offset(300f, 200f))
            repeat(7) { step -> moveTo(0, Offset(300f - (step + 1) * 30f, 200f), delayMillis = delay) }
            up(0)
        }

        val thrown = drag(delay = 16)
        val dragged = drag(delay = 200)

        assertTrue(dragged.center.lon > 0.0, "the drag did not pan")
        assertTrue(
            thrown.center.lon > dragged.center.lon,
            "the flick did not coast past the drag: ${thrown.center.lon} vs ${dragged.center.lon}"
        )
    }
}

/** Zoomed in far enough that a fling is a fraction of a degree rather than a lap of the world. */
private const val START_ZOOM = 12f

/** One gesture on a map of its own, run to a standstill; the camera it left behind is the answer. */
@OptIn(ExperimentalTestApi::class)
private fun gesture(input: TouchInjectionScope.() -> Unit): MapCameraState {
    val camera = MapCameraState(zoom = START_ZOOM)
    runComposeUiTest {
        setContent { Box(Modifier.size(400.dp).testTag("map").mapGestures(camera)) }
        onNodeWithTag("map").performTouchInput(input)
        waitForIdle()
    }
    return camera
}
