package com.coderwise.libs.mapview.tiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.MapCameraState
import com.coderwise.libs.mapview.MapState
import com.coderwise.libs.mapview.MapView
import com.coderwise.libs.mapview.TileKey
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The queue against a real [MapView], which is the only thing that can publish a window: the
 * queue's input is the window the view wants, and its output is what ends up in the slots.
 *
 * It works on real worker threads, so these wait on real time for what they expect to happen, with
 * a generous limit that is only ever reached when something is wrong.
 */
@OptIn(ExperimentalTestApi::class)
class TileQueueTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    /** A 256 dp map at 1x, so a tile is one screen's width and a window is a handful of them. */
    private fun ComposeUiTest.show(camera: MapCameraState, state: MapState<*>) = setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            MapView(camera, Modifier.size(256.dp)) {
                layer(state) { Box(Modifier.fillMaxSize()) }
            }
        }
    }

    private fun ComposeUiTest.await(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting for $what" }
            waitForIdle()
            Thread.sleep(10)
        }
    }

    private fun MapState<*>.windowKeys() = window.keys

    @Test
    fun `every tile the window wants ends up in its slot`() = runComposeUiTest {
        val state = MapState<String>()
        TileQueue(scope, state) { "$it" }
        show(MapCameraState(zoom = 6f), state)

        await("the window to fill") {
            state.windowKeys().isNotEmpty() && state.windowKeys().all { state.peek(it) == "$it" }
        }
    }

    @Test
    fun `the tile nearest the middle of the screen is fetched first`() = runComposeUiTest {
        val state = MapState<String>()
        val order = mutableListOf<TileKey>()
        // One worker, so the order tiles are taken in is the order they are fetched in.
        TileQueue(scope, state, workers = 1) { key ->
            synchronized(order) { order += key }
            "$key"
        }
        show(MapCameraState(zoom = 6f), state)

        await("the window to fill") {
            state.windowKeys().isNotEmpty() && state.windowKeys().all { state.peek(it) != null }
        }
        val window = state.window
        val count = 1 shl window.z
        val centreX = (window.x.first + window.x.last) / 2f
        val centreY = (window.y.first + window.y.last) / 2f
        fun distance(key: TileKey): Float {
            val dx = abs(key.x - centreX).let { minOf(it, count - it) }
            val dy = abs(key.y - centreY)
            return dx * dx + dy * dy
        }
        val first = synchronized(order) { order.first() }
        assertEquals(window.keys.minOf(::distance), distance(first), "fetched $first first")
    }

    @Test
    fun `a tile that is already held is not fetched again`() = runComposeUiTest {
        val state = MapState<String>()
        val calls = mutableMapOf<TileKey, Int>()
        TileQueue(scope, state) { key ->
            synchronized(calls) { calls[key] = (calls[key] ?: 0) + 1 }
            "$key"
        }
        val camera = MapCameraState(zoom = 6f)
        show(camera, state)
        await("the window to fill") {
            state.windowKeys().isNotEmpty() && state.windowKeys().all { state.peek(it) != null }
        }
        val first = state.windowKeys()

        // Away and back: the window is published afresh, over tiles that are already held.
        runOnIdle { camera.moveTo(LatLon(0.0, 40.0), 6f) }
        await("the far window to fill") { state.windowKeys().none { it in first } && state.windowKeys().all { state.peek(it) != null } }
        runOnIdle { camera.moveTo(LatLon(0.0, 0.0), 6f) }
        await("the first window to be back") { state.windowKeys().toSet() == first.toSet() }
        Thread.sleep(300)

        synchronized(calls) {
            first.forEach { assertEquals(1, calls[it], "$it was fetched ${calls[it]} times") }
        }
    }

    @Test
    fun `a tile that failed is not asked for again straight away`() = runComposeUiTest {
        val state = MapState<String>()
        val calls = mutableMapOf<TileKey, Int>()
        TileQueue(scope, state) { key ->
            synchronized(calls) { calls[key] = (calls[key] ?: 0) + 1 }
            null
        }
        val camera = MapCameraState(zoom = 6f)
        show(camera, state)
        await("every tile to have been tried") {
            val keys = state.windowKeys()
            keys.isNotEmpty() && synchronized(calls) { keys.all { calls[it] != null } }
        }
        val first = state.windowKeys()

        runOnIdle { camera.moveTo(LatLon(0.0, 40.0), 6f) }
        await("the far window to have been tried") {
            val keys = state.windowKeys()
            keys.none { it in first } && synchronized(calls) { keys.all { calls[it] != null } }
        }
        runOnIdle { camera.moveTo(LatLon(0.0, 0.0), 6f) }
        await("the first window to be back") { state.windowKeys().toSet() == first.toSet() }
        Thread.sleep(300)

        synchronized(calls) {
            first.forEach { assertEquals(1, calls[it], "$it was tried ${calls[it]} times") }
        }
    }

    @Test
    fun `past its capacity the oldest tiles off the screen are let go`() = runComposeUiTest {
        val state = MapState<String>()
        val capacity = 2
        TileQueue(scope, state, capacity = capacity) { "$it" }
        val camera = MapCameraState(zoom = 6f)
        show(camera, state)
        await("the window to fill") {
            state.windowKeys().isNotEmpty() && state.windowKeys().all { state.peek(it) != null }
        }
        val first = state.windowKeys()
        assertTrue(first.size > capacity, "the test needs a window bigger than the capacity: ${first.size}")
        // The queue notes a tile as held just after it is stored; let the last of them be noted.
        Thread.sleep(200)

        runOnIdle { camera.moveTo(LatLon(0.0, 40.0), 6f) }
        await("the far window to fill") {
            state.windowKeys().none { it in first } && state.windowKeys().all { state.peek(it) != null }
        }

        val left = first.count { state.peek(it) != null }
        assertTrue(left <= capacity, "$left of the old window's ${first.size} tiles are still held")
    }

    @Test
    fun `a tile fetched ahead of the screen goes before one that was shown`() = runComposeUiTest {
        val state = MapState<String>()
        val camera = MapCameraState(zoom = 6f)
        show(camera, state)
        await("a window") { state.windowKeys().isNotEmpty() }
        val a = state.windowKeys()
        // Room for exactly the first window, so that holding anything else means giving some up.
        TileQueue(scope, state, capacity = a.size) { "$it" }
        await("the first window to fill") { a.all { state.peek(it) != null } }

        runOnIdle { camera.moveTo(LatLon(0.0, 40.0), 6f) }
        await("the second window to fill") {
            state.windowKeys().none { it in a } && state.windowKeys().all { state.peek(it) != null }
        }
        val b = state.windowKeys()
        Thread.sleep(200)

        // Back over the first, which is all still held, so nothing is fetched: it is only shown.
        runOnIdle { camera.moveTo(LatLon(0.0, 0.0), 6f) }
        await("the first window to be back") { state.windowKeys().toSet() == a.toSet() }
        Thread.sleep(300)

        // And away to a third. Fetched first but shown last, the first window outranks the second.
        runOnIdle { camera.moveTo(LatLon(0.0, 80.0), 6f) }
        await("the third window to fill") {
            state.windowKeys().none { it in a || it in b } && state.windowKeys().all { state.peek(it) != null }
        }

        assertTrue(a.all { state.peek(it) != null }, "the window shown last was let go")
        assertTrue(b.none { state.peek(it) != null }, "the window shown before it was kept")
    }

    @Test
    fun `tiles shown again are kept over tiles that were only fetched earlier`() = runComposeUiTest {
        val state = MapState<String>()
        val camera = MapCameraState(zoom = 6f)
        show(camera, state)
        await("a window") { state.windowKeys().isNotEmpty() }
        val y = state.windowKeys()
        // One worker, so the tiles of a window land in the order they are taken in.
        TileQueue(scope, state, workers = 1, capacity = y.size) { "$it" }
        await("the first window to fill") { y.all { state.peek(it) != null } }
        Thread.sleep(200)

        // One tile west: half the same ground and a column of it new. The columns the queue
        // fetched first are the western ones, so ground the screen still shows is the ground
        // that was fetched earliest. 360 degrees over 64 tiles.
        runOnIdle { camera.moveTo(LatLon(0.0, -360.0 / 64), 6f) }
        await("the shifted window to fill") {
            state.windowKeys().toSet() != y.toSet() && state.windowKeys().all { state.peek(it) != null }
        }
        val x = state.windowKeys()
        assertEquals(y.size, x.size, "the test needs two windows of one size")
        assertTrue(x.any { it in y } && y.any { it !in x }, "the windows should overlap, not match")
        Thread.sleep(200)

        // Far away: everything held is now off screen, and only the capacity of it can stay.
        runOnIdle { camera.moveTo(LatLon(0.0, 80.0), 6f) }
        await("the far window") { state.windowKeys().none { it in x || it in y } }
        Thread.sleep(300)

        // What the last window showed stays, including the part it shared with the first, which
        // was fetched long before the part that is new. Only what it stopped showing goes.
        assertTrue(x.all { state.peek(it) != null }, "tiles shown last were let go: ${x.filter { state.peek(it) == null }}")
        assertTrue((y - x.toSet()).none { state.peek(it) != null }, "tiles no longer shown were kept")
    }

    @Test
    fun `nothing on screen is let go however small the capacity`() = runComposeUiTest {
        val state = MapState<String>()
        TileQueue(scope, state, capacity = 1) { "$it" }
        show(MapCameraState(zoom = 6f), state)
        await("the window to fill") {
            state.windowKeys().isNotEmpty() && state.windowKeys().all { state.peek(it) != null }
        }
        Thread.sleep(300)
        assertTrue(state.windowKeys().all { state.peek(it) != null })
    }
}
