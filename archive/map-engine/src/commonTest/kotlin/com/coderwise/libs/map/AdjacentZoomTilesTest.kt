package com.coderwise.libs.map

import com.coderwise.libs.mapcore.TileId
import com.coderwise.libs.mapcore.MapMath
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdjacentZoomTilesTest {

    private val berlinLat = 52.5200
    private val berlinLon = 13.4050

    @Test
    fun `returns only tiles at the adjacent integer zoom levels`() {
        val state = TiledMapState(berlinLat, berlinLon, 12.0, initialTileSizePx = 256)

        val tiles = state.adjacentZoomTiles(1000, 800)

        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.zoom == 11 || it.zoom == 13 })
    }

    @Test
    fun `covers the viewport at the moment of crossing up`() {
        val tileSize = 256
        val state = TiledMapState(berlinLat, berlinLon, 12.0, initialTileSizePx = tileSize)
        val width = 1000
        val height = 800

        val tiles = state.adjacentZoomTiles(width, height).filter { it.zoom == 13 }.toSet()

        // Right after crossing up, zoomScale is 1: one tile spans tileSize pixels
        val (cfx, cfy) = MapMath.latLonToTileFractional(berlinLat, berlinLon, 13.0)
        for (sx in listOf(-width / 2.0, 0.0, width / 2.0)) {
            for (sy in listOf(-height / 2.0, 0.0, height / 2.0)) {
                val tx = floor(cfx + sx / tileSize).toInt()
                val ty = floor(cfy + sy / tileSize).toInt()
                assertTrue(TileId(13, tx, ty) in tiles, "missing tile ($tx, $ty) for screen point ($sx, $sy)")
            }
        }
    }

    @Test
    fun `covers the viewport at the moment of crossing down`() {
        val tileSize = 256
        val state = TiledMapState(berlinLat, berlinLon, 12.0, initialTileSizePx = tileSize)
        val width = 1000
        val height = 800

        val tiles = state.adjacentZoomTiles(width, height).filter { it.zoom == 11 }.toSet()

        // Right after crossing down, zoomScale is ~2: one tile spans 2 * tileSize pixels
        val (cfx, cfy) = MapMath.latLonToTileFractional(berlinLat, berlinLon, 11.0)
        for (sx in listOf(-width / 2.0, 0.0, width / 2.0)) {
            for (sy in listOf(-height / 2.0, 0.0, height / 2.0)) {
                val tx = floor(cfx + sx / (2.0 * tileSize)).toInt()
                val ty = floor(cfy + sy / (2.0 * tileSize)).toInt()
                assertTrue(TileId(11, tx, ty) in tiles, "missing tile ($tx, $ty) for screen point ($sx, $sy)")
            }
        }
    }

    @Test
    fun `omits levels outside the zoom bounds`() {
        val minState = TiledMapState(berlinLat, berlinLon, TiledMapState.MIN_ZOOM, initialTileSizePx = 256)
        assertTrue(minState.adjacentZoomTiles(1000, 800).all { it.zoom == 1 })

        val maxState = TiledMapState(berlinLat, berlinLon, TiledMapState.MAX_ZOOM, initialTileSizePx = 256)
        assertTrue(maxState.adjacentZoomTiles(1000, 800).all { it.zoom == 18 })
    }

    @Test
    fun `dedupes wrap-around tiles at low zoom`() {
        val state = TiledMapState(0.0, 0.0, 1.0, initialTileSizePx = 256)

        val tiles = state.adjacentZoomTiles(4000, 3000)

        assertEquals(tiles.size, tiles.toSet().size)
        // Zoom 0 has a single tile in the world
        assertEquals(1, tiles.count { it.zoom == 0 })
    }

    @Test
    fun `empty viewport yields no tiles`() {
        val state = TiledMapState(berlinLat, berlinLon, 12.0, initialTileSizePx = 256)

        assertTrue(state.adjacentZoomTiles(0, 800).isEmpty())
        assertTrue(state.adjacentZoomTiles(1000, 0).isEmpty())
    }
}
