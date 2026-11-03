package com.coderwise.libs.map

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import com.coderwise.libs.mapcore.MapMath
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private class TestFrameClock : MonotonicFrameClock {
    private var nanos = 0L
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        yield()
        nanos += 16_000_000L
        return onFrame(nanos)
    }
}

class TiledMapStateTest {

    private val instantTween = tween<Float>(durationMillis = 0)

    @Test
    fun `animateZoomTo without pivot keeps center`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(51.5074, -0.1278, 10.0)

            state.animateZoomTo(11.0, animationSpec = instantTween)

            assertEquals(11.0, state.zoom, 0.001)
            assertEquals(51.5074, state.latitude, 0.000001)
            assertEquals(-0.1278, state.longitude, 0.000001)
        }
    }

    @Test
    fun `animateZoomTo with center pivot keeps center`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(51.5074, -0.1278, 10.0)

            state.animateZoomTo(11.0, pivot = Offset.Zero, animationSpec = instantTween)

            assertEquals(11.0, state.zoom, 0.001)
            assertEquals(51.5074, state.latitude, 0.000001)
            assertEquals(-0.1278, state.longitude, 0.000001)
        }
    }

    @Test
    fun `animateZoomTo with pivot keeps focal point under pivot`() = runTest {
        withContext(TestFrameClock()) {
            val startLat = 51.5074
            val startLon = -0.1278
            val startZoom = 10.0
            val tileSize = 256
            val pivot = Offset(100f, -50f)
            val state = TiledMapState(startLat, startLon, startZoom, tileSize)

            val focal = focalLatLon(startLat, startLon, startZoom, tileSize, pivot)

            state.animateZoomTo(11.0, pivot = pivot, animationSpec = instantTween)

            val (screenX, screenY) = screenOffsetOf(
                focal.first, focal.second,
                state.latitude, state.longitude, state.zoom, tileSize
            )
            assertEquals(11.0, state.zoom, 0.001)
            assertEquals(pivot.x.toDouble(), screenX, 0.5)
            assertEquals(pivot.y.toDouble(), screenY, 0.5)
        }
    }

    @Test
    fun `animateZoomTo clamps target to max zoom`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 10.0)

            state.animateZoomTo(99.0, animationSpec = instantTween)

            assertEquals(TiledMapState.MAX_ZOOM, state.zoom, 0.001)
        }
    }

    @Test
    fun `animateZoomTo clamps target to min zoom`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 5.0)

            state.animateZoomTo(-99.0, animationSpec = instantTween)

            assertEquals(TiledMapState.MIN_ZOOM, state.zoom, 0.001)
        }
    }

    @Test
    fun `animateLocationTo moves to target`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 10.0)

            state.animateLocationTo(50.0, 25.0, zoom = 12.0, animationSpec = instantTween)

            assertEquals(50.0, state.latitude, 0.000001)
            assertEquals(25.0, state.longitude, 0.000001)
            assertEquals(12.0, state.zoom, 0.001)
        }
    }

    @Test
    fun `updateLocation clamps latitude and wraps longitude`() {
        val state = TiledMapState(0.0, 0.0, 5.0)

        state.setLocation(99.0, 0.0)
        assertEquals(TiledMapState.MAX_LATITUDE, state.latitude, 0.000001)

        state.setLocation(-99.0, 0.0)
        assertEquals(TiledMapState.MIN_LATITUDE, state.latitude, 0.000001)

        state.setLocation(0.0, 181.0)
        assertEquals(-179.0, state.longitude, 0.000001)

        state.setLocation(0.0, -181.0)
        assertEquals(179.0, state.longitude, 0.000001)
    }

    @Test
    fun `constructor wraps longitude`() {
        val state = TiledMapState(0.0, 181.0, 5.0)
        assertEquals(-179.0, state.longitude, 0.000001)
    }

    @Test
    fun `animateLocationTo east across antimeridian takes short path`() = runTest {
        withContext(TestFrameClock()) {
            // 170 → -170 is 20° east, not 340° west.
            val state = TiledMapState(0.0, 170.0, 5.0)

            state.animateLocationTo(0.0, -170.0, animationSpec = instantTween)

            // Final stored longitude is wrapped.
            assertEquals(-170.0, state.longitude, 0.001)
        }
    }

    @Test
    fun `animateLocationTo west across antimeridian takes short path`() = runTest {
        withContext(TestFrameClock()) {
            // -170 → 170 is 20° west, not 340° east.
            val state = TiledMapState(0.0, -170.0, 5.0)

            state.animateLocationTo(0.0, 170.0, animationSpec = instantTween)

            // Final stored longitude is wrapped.
            assertEquals(170.0, state.longitude, 0.001)
        }
    }

    @Test
    fun `stopAnimations cancels running zoom animation`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 10.0)

            val job = launch {
                state.animateZoomTo(15.0, animationSpec = tween(durationMillis = 1000))
            }
            // Let the animation begin
            yield()
            state.stopAnimations()
            job.join()

            // Animation was interrupted before reaching its target.
            assertNotEquals(15.0, state.zoom)
            assertTrue(state.zoom < 15.0)
        }
    }

    @Test
    fun `zoomIn while running stacks on current target`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 10.0)

            // Start a long zoomIn (target = 11) but don't wait for it.
            val first = launch {
                state.animateZoomTo(11.0, animationSpec = tween(durationMillis = 1000))
            }
            yield()
            // Second zoomIn should base off the in-flight target (11), not the current zoom.
            state.zoomIn()
            first.join()

            assertEquals(12.0, state.zoom, 0.001)
        }
    }

    @Test
    fun `applyPan moves center by pixel delta`() {
        val tileSize = 256
        val state = TiledMapState(0.0, 0.0, 5.0, tileSize)

        val (x0, y0) = MapMath.latLonToTileFractional(state.latitude, state.longitude, 5.0)
        state.applyPan(Offset(-tileSize.toFloat(), tileSize.toFloat()))
        val (x1, y1) = MapMath.latLonToTileFractional(state.latitude, state.longitude, 5.0)

        // Pan dragging the map left by one tile width shifts the center one tile right.
        assertEquals(x0 + 1.0, x1, 0.0001)
        assertEquals(y0 - 1.0, y1, 0.0001)
    }

    @Test
    fun `applyPan zero pan is no op`() {
        val state = TiledMapState(51.5074, -0.1278, 10.0)

        state.applyPan(Offset.Zero)

        assertEquals(51.5074, state.latitude, 1e-9)
        assertEquals(-0.1278, state.longitude, 1e-9)
        assertEquals(10.0, state.zoom, 1e-9)
    }

    @Test
    fun `applyPinchZoom factor one is no op`() {
        val state = TiledMapState(51.5074, -0.1278, 10.0)

        state.applyPinchZoom(zoomFactor = 1f, focal = Offset(50f, 50f))

        assertEquals(51.5074, state.latitude, 1e-9)
        assertEquals(-0.1278, state.longitude, 1e-9)
        assertEquals(10.0, state.zoom, 1e-9)
    }

    @Test
    fun `applyPinchZoom doubles zoom at center keeps center`() {
        val state = TiledMapState(51.5074, -0.1278, 10.0)

        state.applyPinchZoom(zoomFactor = 2f, focal = Offset.Zero)

        assertEquals(11.0, state.zoom, 0.001)
        assertEquals(51.5074, state.latitude, 1e-6)
        assertEquals(-0.1278, state.longitude, 1e-6)
    }

    @Test
    fun `applyPinchZoom keeps focal point under pivot`() {
        val startLat = 51.5074
        val startLon = -0.1278
        val startZoom = 10.0
        val tileSize = 256
        val focal = Offset(100f, -50f)
        val state = TiledMapState(startLat, startLon, startZoom, tileSize)

        val pinned = focalLatLon(startLat, startLon, startZoom, tileSize, focal)
        state.applyPinchZoom(zoomFactor = 2f, focal = focal)

        val (screenX, screenY) = screenOffsetOf(
            pinned.first, pinned.second,
            state.latitude, state.longitude, state.zoom, tileSize
        )
        assertEquals(11.0, state.zoom, 0.001)
        assertEquals(focal.x.toDouble(), screenX, 0.5)
        assertEquals(focal.y.toDouble(), screenY, 0.5)
    }

    @Test
    fun `applyPinchZoom clamps to max zoom`() {
        val state = TiledMapState(0.0, 0.0, TiledMapState.MAX_ZOOM)

        state.applyPinchZoom(zoomFactor = 4f, focal = Offset.Zero)

        assertEquals(TiledMapState.MAX_ZOOM, state.zoom, 1e-9)
    }

    @Test
    fun `flingPan moves center in velocity direction`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 5.0)

            // Positive x-velocity moves the map content right → center moves left (−lon).
            state.flingPan(velocityX = 2000f, velocityY = 0f, decaySpec = exponentialDecay())

            assertEquals(0.0, state.latitude, 1e-9)
            assertTrue(state.longitude < 0.0)
        }
    }

    @Test
    fun `flingPan can be cancelled via stopAnimations`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 5.0)

            val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                state.flingPan(velocityX = 2000f, velocityY = 0f, decaySpec = exponentialDecay())
            }
            // Let the animation tick at least once.
            yield()
            yield()
            state.stopAnimations()
            job.join()

            assertTrue(job.isCancelled)
        }
    }

    @Test
    fun `bearing setter wraps to 0 until 360`() {
        val state = TiledMapState(0.0, 0.0, 5.0)

        state.bearing = 370.0
        assertEquals(10.0, state.bearing, 1e-9)

        state.bearing = -90.0
        assertEquals(270.0, state.bearing, 1e-9)
    }

    @Test
    fun `applyPan with east-up bearing pans along rotated axes`() {
        val tileSize = 256
        val state = TiledMapState(0.0, 0.0, 5.0, tileSize)
        state.bearing = 90.0

        val (x0, y0) = MapMath.latLonToTileFractional(state.latitude, state.longitude, 5.0)
        // With east at the top of the screen, dragging the content left moves the center
        // toward screen-right, which is geographic south (one tile down in tile space).
        state.applyPan(Offset(-tileSize.toFloat(), 0f))
        val (x1, y1) = MapMath.latLonToTileFractional(state.latitude, state.longitude, 5.0)

        assertEquals(x0, x1, 0.0001)
        assertEquals(y0 + 1.0, y1, 0.0001)
    }

    @Test
    fun `applyRotation around center keeps center and turns bearing`() {
        val state = TiledMapState(51.5074, -0.1278, 10.0)

        state.applyRotation(degrees = 45.0, focal = Offset.Zero)

        assertEquals(45.0, state.bearing, 1e-9)
        assertEquals(51.5074, state.latitude, 1e-9)
        assertEquals(-0.1278, state.longitude, 1e-9)
    }

    @Test
    fun `applyRotation keeps focal point under pivot`() {
        val startLat = 51.5074
        val startLon = -0.1278
        val startZoom = 10.0
        val tileSize = 256
        val focal = Offset(100f, -50f)
        val state = TiledMapState(startLat, startLon, startZoom, tileSize)

        val pinned = focalLatLon(startLat, startLon, startZoom, tileSize, focal)
        state.applyRotation(degrees = 45.0, focal = focal)

        val (screenX, screenY) = screenOffsetOf(
            pinned.first, pinned.second,
            state.latitude, state.longitude, state.zoom, tileSize,
            bearingDegrees = state.bearing
        )
        assertEquals(45.0, state.bearing, 1e-9)
        assertEquals(focal.x.toDouble(), screenX, 0.5)
        assertEquals(focal.y.toDouble(), screenY, 0.5)
    }

    @Test
    fun `applyPinchZoom keeps focal point under pivot when rotated`() {
        val startLat = 51.5074
        val startLon = -0.1278
        val startZoom = 10.0
        val tileSize = 256
        val focal = Offset(100f, -50f)
        val state = TiledMapState(startLat, startLon, startZoom, tileSize)
        state.bearing = 90.0

        val pinned = focalLatLon(startLat, startLon, startZoom, tileSize, focal, bearingDegrees = 90.0)
        state.applyPinchZoom(zoomFactor = 2f, focal = focal)

        val (screenX, screenY) = screenOffsetOf(
            pinned.first, pinned.second,
            state.latitude, state.longitude, state.zoom, tileSize,
            bearingDegrees = 90.0
        )
        assertEquals(11.0, state.zoom, 0.001)
        assertEquals(focal.x.toDouble(), screenX, 0.5)
        assertEquals(focal.y.toDouble(), screenY, 0.5)
    }

    @Test
    fun `animateBearingTo reaches target`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 5.0)
            state.bearing = 350.0

            state.animateBearingTo(10.0, animationSpec = instantTween)

            assertEquals(10.0, state.bearing, 0.001)
        }
    }

    @Test
    fun `animateBearingTo crosses north on the short path`() = runTest {
        withContext(TestFrameClock()) {
            val state = TiledMapState(0.0, 0.0, 5.0)
            state.bearing = 350.0

            val job = launch {
                state.animateBearingTo(10.0, animationSpec = tween(durationMillis = 1000))
            }
            yield()
            yield()
            state.stopAnimations()
            job.join()

            // Mid-flight the bearing must sit on the 20° arc through north, never near 180.
            assertTrue(state.bearing >= 350.0 || state.bearing < 10.0)
        }
    }

    @Test
    fun `isLatLonOnScreen accounts for bearing`() {
        val tileSize = 256
        val state = TiledMapState(0.0, 0.0, 5.0, tileSize)
        // One tile east of center at zoom 5 = 360/32 degrees = 256 px from center.
        val pointLon = 360.0 / 32.0

        // Tall, narrow viewport: 256 px east is outside the 400 px width when north is up...
        assertTrue(!state.isLatLonOnScreen(0.0, pointLon, viewportWidth = 400, viewportHeight = 1000))

        // ...but rotating east to the top moves the point onto the long screen axis.
        state.bearing = 90.0
        assertTrue(state.isLatLonOnScreen(0.0, pointLon, viewportWidth = 400, viewportHeight = 1000))
    }

    @Test
    fun `adjacentZoomTiles expands coverage when rotated`() {
        val state = TiledMapState(51.5074, -0.1278, 10.0, 256)

        val tilesNorthUp = state.adjacentZoomTiles(viewportWidth = 1000, viewportHeight = 500)
        state.bearing = 45.0
        val tilesRotated = state.adjacentZoomTiles(viewportWidth = 1000, viewportHeight = 500)

        // At 45° the rotated viewport's bounding box grows in both axes, so the unrotated
        // tile set is a strict subset.
        assertTrue(tilesRotated.containsAll(tilesNorthUp))
        assertTrue(tilesRotated.size > tilesNorthUp.size)
    }

    @Test
    fun `rotationLockAccumulator suppresses twist below threshold`() {
        val lock = RotationLockAccumulator(thresholdDegrees = 10f)

        assertEquals(0f, lock.update(4f))
        assertEquals(0f, lock.update(4f))
        assertTrue(!lock.unlocked)

        // Crossing the threshold unlocks but discards what was accumulated...
        assertEquals(0f, lock.update(4f))
        assertTrue(lock.unlocked)

        // ...and subsequent deltas pass through unchanged.
        assertEquals(2.5f, lock.update(2.5f))
    }

    @Test
    fun `touchSlopAccumulator passes after pan threshold`() {
        val slop = TouchSlopAccumulator(touchSlop = 10f)

        slop.update(Offset(4f, 0f), zoomFactor = 1f, centroidSize = 50f)
        assertTrue(!slop.passed)

        slop.update(Offset(8f, 0f), zoomFactor = 1f, centroidSize = 50f)
        assertTrue(slop.passed)
    }

    @Test
    fun `touchSlopAccumulator passes after zoom threshold`() {
        val slop = TouchSlopAccumulator(touchSlop = 10f)

        slop.update(Offset.Zero, zoomFactor = 1.05f, centroidSize = 100f)
        // 0.05 * 100 = 5 < 10
        assertTrue(!slop.passed)

        slop.update(Offset.Zero, zoomFactor = 1.10f, centroidSize = 100f)
        // accumulated 1.155, |1 - 1.155| * 100 = 15.5 > 10
        assertTrue(slop.passed)
    }

    private fun focalLatLon(
        centerLat: Double, centerLon: Double, zoom: Double, tileSize: Int, pivot: Offset,
        bearingDegrees: Double = 0.0
    ): Pair<Double, Double> {
        val zoomInt = zoom.toInt()
        val scale = 2.0.pow(zoom - zoomInt)
        val (centerTileX, centerTileY) = MapMath.latLonToTileFractional(
            centerLat, centerLon, zoomInt.toDouble()
        )
        val radians = bearingDegrees * PI / 180.0
        val worldX = pivot.x * cos(radians) - pivot.y * sin(radians)
        val worldY = pivot.x * sin(radians) + pivot.y * cos(radians)
        val focalTileX = centerTileX + worldX / (tileSize * scale)
        val focalTileY = centerTileY + worldY / (tileSize * scale)
        return MapMath.tileToLatLon(focalTileX, focalTileY, zoomInt.toDouble())
    }

    private fun screenOffsetOf(
        focalLat: Double, focalLon: Double,
        centerLat: Double, centerLon: Double, zoom: Double, tileSize: Int,
        bearingDegrees: Double = 0.0
    ): Pair<Double, Double> {
        val zoomInt = zoom.toInt()
        val scale = 2.0.pow(zoom - zoomInt)
        val (centerTileX, centerTileY) = MapMath.latLonToTileFractional(
            centerLat, centerLon, zoomInt.toDouble()
        )
        val (focalTileX, focalTileY) = MapMath.latLonToTileFractional(
            focalLat, focalLon, zoomInt.toDouble()
        )
        val worldX = (focalTileX - centerTileX) * tileSize * scale
        val worldY = (focalTileY - centerTileY) * tileSize * scale
        val radians = bearingDegrees * PI / 180.0
        return (worldX * cos(radians) + worldY * sin(radians)) to
            (-worldX * sin(radians) + worldY * cos(radians))
    }
}
