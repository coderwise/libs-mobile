package com.coderwise.libs.mapview.tiles.raster

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * One slot's worth of image tile: blits the [src] fraction of an already-decoded [tile].
 *
 * Decoding is the caller's, upstream of the view — `ByteArray.decodeToImageBitmap()` is one line —
 * so a tile is decoded once when it arrives rather than whenever a slot happens to want it.
 */
@Composable
fun RasterSlot(tile: ImageBitmap, src: Rect, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawImage(
            image = tile,
            srcOffset = IntOffset((src.left * tile.width).roundToInt(), (src.top * tile.height).roundToInt()),
            srcSize = IntSize((src.width * tile.width).roundToInt(), (src.height * tile.height).roundToInt()),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        )
    }
}
