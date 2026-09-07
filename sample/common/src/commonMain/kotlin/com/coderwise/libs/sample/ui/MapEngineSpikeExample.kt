package com.coderwise.libs.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.dp
import com.coderwise.experiment.mapengine.LatLon
import com.coderwise.experiment.mapengine.MapState
import com.coderwise.experiment.mapengine.MapView
import com.coderwise.experiment.mapengine.TileKey
import com.coderwise.experiment.mapengine.ZOOM_LIMITS
import com.coderwise.experiment.mapengine.mapGestures
import com.coderwise.experiment.mapengine.rememberMapCameraState
import com.coderwise.experiment.maptiles.TileQueue
import com.coderwise.experiment.maptiles.raster.RasterSlot
import com.coderwise.experiment.maptiles.shown
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The map engine experiment, consumed the way another project would: as published artifacts under
 * `com.coderwise.experiment`, not as modules of this build. It is here to prove the integration —
 * that the artifacts resolve and run on every target this sample builds for.
 *
 * Unlike the `:map-engine` example above it, this one is on the real map, because fetching is the
 * half of the integration worth proving: the engine fetches nothing itself. `TileQueue` is handed
 * a `suspend (TileKey) -> ImageBitmap?` and does not care what happens inside it, so what a tile
 * costs to get — the request, the decode — is entirely this file's business.
 */
@Composable
internal fun MapEngineSpikeExample() {
    val camera = rememberMapCameraState(center = LatLon(52.5200, 13.4050), zoom = 11f)
    val scope = rememberCoroutineScope()
    val tiles = remember { MapState<ImageBitmap>(tileSize = 256, zoomRange = 0..19) }

    val http = remember { HttpClient() }
    DisposableEffect(http) { onDispose { http.close() } }
    // The queue is the only thing that fills the state: it takes the window the view publishes,
    // fetches what is nearest the middle of it first, and drops what leaves the screen unstarted.
    remember { TileQueue(scope, tiles, capacity = 96) { key -> osmTile(http, key) } }

    Box(Modifier.fillMaxSize().mapGestures(camera)) {
        MapView(camera, tiles, Modifier.fillMaxSize()) {
            // One layer draws the tiles; the next goes over all of them, so a tile placed later
            // cannot paint over it. What a slot shows while its own tile is still coming — here
            // the nearest ancestor, magnified — is `shown`.
            layer { key -> tiles.shown(key)?.let { RasterSlot(it.content, it.src) } }
            layer { key -> TileName(key) }
        }
        ZoomControls(
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
            onZoomIn = { camera.moveTo(camera.center, (camera.zoom + 1f).coerceIn(ZOOM_LIMITS)) },
            onZoomOut = { camera.moveTo(camera.center, (camera.zoom - 1f).coerceIn(ZOOM_LIMITS)) }
        )
        // Not decoration: OpenStreetMap's tile policy requires the credit on screen.
        Plate("© OpenStreetMap contributors", Modifier.align(Alignment.BottomEnd).padding(8.dp))
    }
}

/** Which tile is which, over the map rather than inside it — the second layer, doing something. */
@Composable
private fun TileName(key: TileKey) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Plate("${key.z}/${key.x}/${key.y}")
    }
}

@Composable
private fun Plate(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

/**
 * One tile off the standard OpenStreetMap layer, decoded on the way in so that the state holds
 * tiles ready to draw. `decodeToImageBitmap` is Compose's own, in common code since 1.12.
 *
 * The tile policy wants an identifying User-Agent and no bulk downloading — a sample panned around
 * by hand is well inside it. Browsers do not let a page set that header, so the web builds
 * identify themselves by Referer instead, which is what the policy expects of a browser.
 * https://operations.osmfoundation.org/policies/tiles/
 */
private suspend fun osmTile(http: HttpClient, key: TileKey): ImageBitmap? = runCatching {
    val response = http.get("https://tile.openstreetmap.org/${key.z}/${key.x}/${key.y}.png") {
        header(HttpHeaders.UserAgent, USER_AGENT)
    }
    if (!response.status.isSuccess()) return@runCatching null
    val bytes = response.readRawBytes()
    withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
}.getOrNull()

private const val USER_AGENT = "coderwise-libs-sample/1.0 (map engine experiment)"
