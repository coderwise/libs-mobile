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
