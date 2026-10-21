package com.coderwise.libs.mapview.tiles.raster

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RasterSlot] drawn for real: a tile whose four quarters are four colours, so which quarter lands
 * where is a matter of reading pixels.
 */
@OptIn(ExperimentalTestApi::class)
class RasterSlotTest {

    private val topLeft = Color.Red
    private val topRight = Color.Green
    private val bottomLeft = Color.Blue
    private val bottomRight = Color.Yellow

    private val side = 100

    /** An 8x8 tile in four flat quarters, kept clear of any resampling at the seams by its size. */
    private fun tile(): ImageBitmap {
        val bitmap = ImageBitmap(8, 8)
        val canvas = Canvas(bitmap)
        fun quarter(x: Float, y: Float, color: Color) =
            canvas.drawRect(x, y, x + 4f, y + 4f, Paint().apply { this.color = color })
        quarter(0f, 0f, topLeft)
        quarter(4f, 0f, topRight)
        quarter(0f, 4f, bottomLeft)
        quarter(4f, 4f, bottomRight)
        return bitmap
    }

    /** The colour at the middle of each quarter of a [side] px slot showing [src], row by row. */
    private fun shown(src: Rect): List<Color> {
        var pixels: List<Color> = emptyList()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    RasterSlot(tile(), src, Modifier.size(side.dp))
                }
            }
            val image = onRoot().captureToImage().toPixelMap()
            // A point well inside each quarter of the slot: the four corners' neighbourhoods.
            pixels = listOf(
                image[side / 4, side / 4], image[3 * side / 4, side / 4],
                image[side / 4, 3 * side / 4], image[3 * side / 4, 3 * side / 4]
            ).map { it.copy(alpha = 1f) }
        }
        return pixels
    }

    @Test
    fun `the whole tile fills the slot, each quarter where it belongs`() {
        assertEquals(
            listOf(topLeft, topRight, bottomLeft, bottomRight),
            shown(Rect(0f, 0f, 1f, 1f))
        )
    }

    @Test
    fun `a quarter of the tile is stretched over the whole slot`() {
        val topRightQuarter = Rect(0.5f, 0f, 1f, 0.5f)
        assertEquals(List(4) { topRight }, shown(topRightQuarter))

        val bottomLeftQuarter = Rect(0f, 0.5f, 0.5f, 1f)
        assertEquals(List(4) { bottomLeft }, shown(bottomLeftQuarter))
    }

    @Test
    fun `a half of the tile shows the two quarters in it`() {
        val bottomHalf = Rect(0f, 0.5f, 1f, 1f)
        assertEquals(listOf(bottomLeft, bottomRight, bottomLeft, bottomRight), shown(bottomHalf))
    }
}
