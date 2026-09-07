package com.coderwise.libs.sample.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coderwise.experiment.mapengine.LatLon
import com.coderwise.experiment.mapengine.MapState
import com.coderwise.experiment.mapengine.MapView
import com.coderwise.experiment.mapengine.TileKey
import com.coderwise.experiment.mapengine.mapGestures
import com.coderwise.experiment.mapengine.rememberMapCameraState
import com.coderwise.experiment.maptiles.TileQueue
import com.coderwise.experiment.maptiles.raster.RasterSlot
import com.coderwise.experiment.maptiles.shown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The map engine spike, consumed the way another project would: as published artifacts under
 * `com.coderwise.experiment`, not as modules of this build. It is here to prove the integration —
 * that the artifacts resolve and run on every target this sample builds for.
 *
 * Tiles are drawn locally, one bitmap per tile, so this needs no network, tile server or API key,
 * exactly like the `:map-engine` example above it. The `delay` in the fetch is where a request
 * would go, and is what makes the queue visible: tiles arrive a few at a time, and until one does
 * its slot shows a magnified ancestor rather than a hole.
 */
@Composable
internal fun MapEngineSpikeExample() {
    val camera = rememberMapCameraState(center = LatLon(52.5200, 13.4050), zoom = 4f)
    val scope = rememberCoroutineScope()

    val even = MaterialTheme.colorScheme.surfaceVariant
    val odd = MaterialTheme.colorScheme.surfaceContainerHighest
    val edge = MaterialTheme.colorScheme.outlineVariant

    val tiles = remember { MapState<ImageBitmap>(tileSize = 256, zoomRange = 0..12) }
    // The queue is the only thing that fills the state, and what it fills it with is whatever the
    // lambda returns — bytes, or, as here, a tile already decoded and ready to draw.
    remember(even, odd, edge) {
        TileQueue(scope, tiles, capacity = 64) { key ->
            delay(150.milliseconds)
            checkerboard(key, even, odd, edge)
        }
    }

    Box(Modifier.fillMaxSize().mapGestures(camera)) {
        MapView(camera, tiles, Modifier.fillMaxSize()) {
            // One layer draws the tiles; the next goes over all of them. What a slot shows while
            // its own tile is still coming — here the nearest ancestor, magnified — is `shown`.
            layer { key -> tiles.shown(key)?.let { RasterSlot(it.content, it.src) } }
            layer { key -> TileName(key) }
        }
        CameraLine(
            text = "zoom ${camera.zoom.toInt()}   " +
                "${camera.center.lat.formatted(2)}, ${camera.center.lon.formatted(2)}",
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )
        ZoomControls(
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
            onZoomIn = {
                scope.launch {
                    animate(camera.zoom, camera.zoom + 1f) { zoom, _ ->
                        camera.moveTo(camera.center, zoom)
                    }
                }
            },
            onZoomOut = {
                scope.launch {
                    animate(camera.zoom, camera.zoom - 1f) { zoom, _ ->
                        camera.moveTo(camera.center, zoom)
                    }
                }
            }
        )
    }
}

/** A layer of its own, so a tile's name is never painted over by the tile placed after it. */
@Composable
private fun TileName(key: TileKey) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "${key.z}/${key.x}/${key.y}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CameraLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/**
 * One tile, drawn into a bitmap by the queue's worker — where a real source would be decoding
 * bytes. Compose can draw into an [ImageBitmap] in common code, so this needs no `expect`.
 */
private fun checkerboard(key: TileKey, even: Color, odd: Color, edge: Color): ImageBitmap {
    val side = 256
    val bitmap = ImageBitmap(side, side)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = Size(side.toFloat(), side.toFloat())
    ) {
        drawRect(if ((key.x + key.y) % 2 == 0) even else odd)
        drawRect(edge, style = Stroke(2f))
    }
    return bitmap
}
