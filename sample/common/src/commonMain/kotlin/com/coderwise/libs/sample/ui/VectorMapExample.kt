package com.coderwise.libs.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.coderwise.libs.mapcore.MapMath
import com.coderwise.libs.mapview.LatLon
import com.coderwise.libs.mapview.MapCameraState
import com.coderwise.libs.mapview.MapState
import com.coderwise.libs.mapview.MapView
import com.coderwise.libs.mapview.TileKey
import com.coderwise.libs.mapview.ZOOM_LIMITS
import com.coderwise.libs.mapview.mapGestures
import com.coderwise.libs.mapview.rememberMapCameraState
import com.coderwise.libs.mapview.tiles.TileQueue
import com.coderwise.libs.mapview.tiles.shown
import com.coderwise.libs.mapview.tiles.vector.MapFeature
import com.coderwise.libs.mapview.tiles.vector.VectorLabels
import com.coderwise.libs.mapview.tiles.vector.VectorSlot
import com.coderwise.libs.mapview.tiles.vector.VectorStyle
import com.coderwise.libs.mapview.tiles.vector.VectorTile
import com.coderwise.libs.mapview.tiles.vector.decodeVectorTile
import com.coderwise.libs.mapview.tiles.vector.featuresAt
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor

/**
 * The vector tile slot: the same `MapView` and the same `TileQueue` as the raster example, with a
 * slot that draws decoded geometry instead of a bitmap.
 *
 * Four things a vector tile can do that a picture of a map cannot, all of them on screen here:
 *
 * - **It knows what it is drawing.** Tap the map and `featuresAt` says what is under the finger —
 *   the building, the road and its name, the wood, the lake — read from the same data the map is
 *   drawn from rather than from a second lookup service.
 * - **Names are text, and they follow the map.** Place names stay level however the map is turned;
 *   a road name lies along the straightest run of its road.
 * - **It knows where the land stops.** The water toggle draws each tile's `water` path, which is
 *   what anything drawn over the sea — a coverage overlay, a scratch map — needs in order not to
 *   cover it.
 *
 * Buildings appear from zoom 15, where they are big enough to be worth outlining.
 */
@Composable
internal fun VectorMapExample() {
    val camera = rememberMapCameraState(center = LatLon(51.5074, -0.1278), zoom = 14f)
    val scope = rememberCoroutineScope()
    // Only to 14: OpenFreeMap's planet tiles stop there, and past it the grid lays the deepest
    // level out bigger rather than asking for tiles that do not exist.
    val tiles = remember { MapState<VectorTile>(zoomRange = 0..14) }
    // One style for the whole map, held still: it is baked into a decoded tile's paths, so a style
    // rebuilt per composition would re-decode every tile on screen every frame.
    val style = remember { VectorStyle() }

    val http = remember { HttpClient() }
    DisposableEffect(http) { onDispose { http.close() } }
    remember { TileQueue(scope, tiles, capacity = 64) { key -> vectorTile(http, key, style) } }

    VectorMapContent(camera, tiles, scope) { at ->
        // What the tile says is under the finger. The bytes are fetched again rather than kept:
        // this runs on a tap, not on a frame, and the tile is in the HTTP cache by now.
        val bytes = vectorTileBytes(http, at.tile) ?: return@VectorMapContent emptyList()
        withContext(Dispatchers.Default) {
            featuresAt(bytes, at.x, at.y, radius = PRESS_RADIUS)
        }
    }
}

@Composable
private fun VectorMapContent(
    camera: MapCameraState,
    tiles: MapState<VectorTile>,
    scope: kotlinx.coroutines.CoroutineScope,
    identify: suspend (Press) -> List<MapFeature>
) {
    var showWater by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<MapFeature>?>(null) }

    Box(
        Modifier.fillMaxSize().mapGestures(camera) { at ->
            found = null
            scope.launch { found = identify(at.pressOn(camera.zoom)) }
        }
    ) {
        MapView(camera, Modifier.fillMaxSize()) {
            layer(tiles) { key -> tiles.shown(key)?.let { VectorSlot(it.content, it.src) } }
            // Water over the tiles it came from, so the shape reads against the map it belongs to.
            if (showWater) {
                layer(tiles) { key ->
                    tiles.shown(key)?.let { shown ->
                        shown.content.water?.let { WaterShape(it, shown.src) }
                    }
                }
            }
            // Names in a layer of their own, over every tile: a name sits where its town is, which
            // is as often as not across a seam.
            layer(tiles) { key ->
                tiles.shown(key)?.let {
                    VectorLabels(tile = it.content, src = it.src, bearing = camera.bearing)
                }
            }
        }

        ZoomControls(
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
            onZoomIn = { camera.moveTo(camera.center, (camera.zoom + 1f).coerceIn(ZOOM_LIMITS)) },
            onZoomOut = { camera.moveTo(camera.center, (camera.zoom - 1f).coerceIn(ZOOM_LIMITS)) }
        )

        FilterChip(
            selected = showWater,
            onClick = { showWater = !showWater },
            label = { Text("Water shape") },
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )

        found?.let { features ->
            FeatureCard(features, Modifier.align(Alignment.BottomStart).padding(12.dp))
        }

        // OpenFreeMap serves OpenStreetMap data, and asks for both to be credited.
        Plate(
            "© OpenFreeMap © OpenStreetMap contributors",
            Modifier.align(Alignment.BottomEnd).padding(8.dp)
        )
    }
}

/** What a tap turned up, in the order the query ranks it: most specific thing first. */
@Composable
private fun FeatureCard(features: List<MapFeature>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (features.isEmpty()) {
            Text("nothing here", style = MaterialTheme.typography.labelMedium)
            return@Column
        }
        features.forEach { feature ->
            val what = feature.name ?: feature.featureClass ?: "—"
            Text(
                "${feature.kind.name.lowercase()}: $what",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** The tile's water, drawn over the slot that carries it: the path is in `0..1` tile coordinates. */
@Composable
private fun WaterShape(water: Path, src: Rect) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            // The same walk the slot makes: the whole tile scaled to the slot, then the part of it
            // this slot shows brought into view.
            val side = size.width / src.width
            translate(-src.left * side, -src.top * side) {
                scale(side, side, pivot = Offset.Zero) { drawPath(water, WATER_MARK) }
            }
        }
    )
}

/** A press, as the tile it landed in and where in that tile it landed. */
private class Press(val tile: TileKey, val x: Float, val y: Float)

/**
 * Where a tap falls, in the terms the query asks for: the vector module knows nothing about the
 * world a tile was cut from, so turning a coordinate into a tile and a fraction of it is the
 * caller's half of the bargain.
 */
private fun LatLon.pressOn(zoom: Float): Press {
    val z = floor(zoom).toInt().coerceIn(0, 14)
    val (x, y) = MapMath.latLonToTileFractional(lat, lon, z.toDouble())
    val count = 1 shl z
    return Press(
        tile = TileKey(z, x.toInt().mod(count), y.toInt().coerceIn(0, count - 1)),
        x = (x - floor(x)).toFloat(),
        y = (y - floor(y)).toFloat()
    )
}

private suspend fun vectorTile(http: HttpClient, key: TileKey, style: VectorStyle): VectorTile? {
    val bytes = vectorTileBytes(http, key) ?: return null
    // Tens of milliseconds a tile: a protobuf parse plus a few thousand path segments, which is
    // exactly the work that must not happen on the thread drawing the frames.
    return withContext(Dispatchers.Default) { decodeVectorTile(key.z, bytes, style) }
}

/**
 * One tile of OpenFreeMap's planet build — OpenStreetMap data, free, no key. The dated path is a
 * release; read a current one from https://tiles.openfreemap.org/planet when this goes stale.
 */
private suspend fun vectorTileBytes(http: HttpClient, key: TileKey): ByteArray? = runCatching {
    val response = http.get("$OPENFREEMAP/${key.z}/${key.x}/${key.y}.pbf")
    if (!response.status.isSuccess()) return@runCatching null
    response.readRawBytes().takeIf { it.isNotEmpty() }
}.getOrNull()

private const val OPENFREEMAP = "https://tiles.openfreemap.org/planet/20260906_080001_pt"

/**
 * How wide a press is, as a fraction of a tile. A finger is not pixel-exact and a road is a few
 * metres wide, so a line has to be catchable by one; an area answers by containment and ignores it.
 */
private const val PRESS_RADIUS = 0.01f

/** Enough to read as water over the map, not enough to hide what is under it. */
private val WATER_MARK = Color(0x662D6CDF)
