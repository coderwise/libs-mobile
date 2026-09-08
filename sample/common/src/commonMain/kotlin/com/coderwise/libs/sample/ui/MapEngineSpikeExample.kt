package com.coderwise.libs.sample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coderwise.libs.experiment.map.LatLon
import com.coderwise.libs.experiment.map.MapCameraState
import com.coderwise.libs.experiment.map.MapState
import com.coderwise.libs.experiment.map.MapView
import com.coderwise.libs.experiment.map.Polyline
import com.coderwise.libs.experiment.map.TileKey
import com.coderwise.libs.experiment.map.ZOOM_LIMITS
import com.coderwise.libs.experiment.map.mapGestures
import com.coderwise.libs.experiment.map.rememberMapCameraState
import com.coderwise.libs.experiment.map.tiles.TileQueue
import com.coderwise.libs.experiment.map.tiles.raster.RasterSlot
import com.coderwise.libs.experiment.map.tiles.shown
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The map engine experiment, on the real map and on every target this sample builds for.
 *
 * Fetching is the half worth showing, because the engine does none of it: `TileQueue` is handed a
 * `suspend (TileKey) -> ImageBitmap?` and does not care what happens inside it, so what a tile
 * costs to get — the request, the decode — is entirely this file's business.
 *
 * The rest is what a map carries that no tile knows about: two routes, four search results, and a
 * pin wherever the map itself is tapped. Each is an ordinary composable or an ordinary line, told
 * only where it belongs.
 */
@Composable
internal fun MapEngineSpikeExample() {
    val camera = rememberMapCameraState(center = LatLon(52.5200, 13.4050), zoom = 11f)
    val scope = rememberCoroutineScope()
    val tiles = remember { MapState<ImageBitmap>(zoomRange = 0..19) }

    val http = remember { HttpClient() }
    DisposableEffect(http) { onDispose { http.close() } }
    // The queue is the only thing that fills the state: it takes the window the view publishes,
    // fetches what is nearest the middle of it first, and drops what leaves the screen unstarted.
    remember { TileQueue(scope, tiles, capacity = 96) { key -> osmTile(http, key) } }

    MapEngineSpikeContent(
        camera = camera,
        tiles = tiles,
        onZoomIn = { camera.moveTo(camera.center, (camera.zoom + 1f).coerceIn(ZOOM_LIMITS)) },
        onZoomOut = { camera.moveTo(camera.center, (camera.zoom - 1f).coerceIn(ZOOM_LIMITS)) }
    )
}

@Composable
private fun MapEngineSpikeContent(
    camera: MapCameraState,
    tiles: MapState<ImageBitmap>,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    // What the last tap picked out: a route, one of the places, or a coordinate of its own.
    var picked by remember { mutableStateOf<Any?>(null) }
    var dropped by remember { mutableStateOf<LatLon?>(null) }

    Box(
        modifier.fillMaxSize().mapGestures(camera) { at ->
            // A tap nothing on the map took: clear the selection and leave a pin where it landed.
            picked = null
            dropped = at
        }
    ) {
        MapView(camera, Modifier.fillMaxSize()) {
            // One layer draws the tiles; the next goes over all of them, so a tile placed later
            // cannot paint over it. What a slot shows while its own tile is still coming — here
            // the nearest ancestor, magnified — is `shown`.
            layer(tiles) { key -> tiles.shown(key)?.let { RasterSlot(it.content, it.src) } }
            layer(tiles) { key -> TileName(key) }
            // None of what follows came from a tile: routes, pins and labels, put where they are.
            overlay {
                ROUTES.forEach { route ->
                    Polyline(
                        points = route.points,
                        color = if (picked == route) PICKED else route.color,
                        width = if (picked == route) 9.dp else 5.dp,
                        onClick = { picked = route }
                    )
                }
                // Search results: pins whose tip is the coordinate, tappable like anything else in
                // a layout, with the name of the one picked shown over it.
                PLACES.forEach { place ->
                    val selected = picked == place
                    Pin(
                        color = if (selected) PICKED else PLACE,
                        modifier = Modifier
                            .at(place.at, Alignment.BottomCenter)
                            .clickable { picked = place }
                    )
                    if (selected) {
                        Plate(place.name, Modifier.at(place.at, Alignment.TopCenter))
                    }
                }
                dropped?.let { at ->
                    Pin(color = DROPPED, modifier = Modifier.at(at, Alignment.BottomCenter))
                    Plate(at.readable, Modifier.at(at, Alignment.TopCenter))
                }
            }
        }
        ZoomControls(
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut
        )
        // Not decoration: OpenStreetMap's tile policy requires the credit on screen.
        Plate("© OpenStreetMap contributors", Modifier.align(Alignment.BottomEnd).padding(8.dp))
    }
}

/** A teardrop whose point is the coordinate, which is why it is anchored by its bottom. */
@Composable
private fun Pin(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp, 28.dp)) {
        val radius = size.width / 2
        drawPath(
            path = Path().apply {
                moveTo(radius, size.height)
                lineTo(radius * 0.3f, radius * 1.6f)
                lineTo(radius * 1.7f, radius * 1.6f)
                close()
            },
            color = color
        )
        drawCircle(color, radius, Offset(radius, radius))
        drawCircle(Color.White, radius * 0.35f, Offset(radius, radius))
        drawCircle(Color.White, radius, Offset(radius, radius), style = Stroke(size.width * 0.1f))
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

/** A place a search might have turned up: a name and where it is. */
private data class Place(val name: String, val at: LatLon)

/** A line with a name, so that picking one out has something to show. */
private class Route(val points: List<LatLon>, val color: Color)

private val PLACES = listOf(
    Place("Brandenburger Tor", LatLon(52.5163, 13.3777)),
    Place("Museumsinsel", LatLon(52.5212, 13.3971)),
    Place("Alexanderplatz", LatLon(52.5219, 13.4132)),
    Place("Potsdamer Platz", LatLon(52.5096, 13.3757))
)

/**
 * Two lines that cross, because where they both fall under one finger the nearer one takes the
 * tap — the engine decides that, not the order they are drawn in.
 */
private val ROUTES = listOf(
    // East from the gate, along the Spree.
    Route(
        color = Color(0xFF2D6CDF),
        points = listOf(
            LatLon(52.5163, 13.3777),
            LatLon(52.5170, 13.3830),
            LatLon(52.5186, 13.3888),
            LatLon(52.5196, 13.3960),
            LatLon(52.5185, 13.4040),
            LatLon(52.5165, 13.4090),
            LatLon(52.5155, 13.4180)
        )
    ),
    // North from Potsdamer Platz, across it.
    Route(
        color = Color(0xFF1E9E58),
        points = listOf(
            LatLon(52.5010, 13.3730),
            LatLon(52.5096, 13.3757),
            LatLon(52.5180, 13.3800),
            LatLon(52.5250, 13.3900),
            LatLon(52.5320, 13.4050)
        )
    )
)

private val PICKED = Color(0xFFE8A100)
private val PLACE = Color(0xFFD1453B)
private val DROPPED = Color(0xFF6C4AB6)

/** Four decimal places is about ten metres, which is as much as a tap can mean. */
private val LatLon.readable: String
    get() = "${(lat * 1e4).roundToInt() / 1e4}, ${(lon * 1e4).roundToInt() / 1e4}"

private const val USER_AGENT = "coderwise-libs-sample/1.0 (map engine experiment)"

@Preview
@Composable
private fun MapEngineSpikeExamplePreview() {
    MaterialTheme {
        MapEngineSpikeContent(
            camera = rememberMapCameraState(center = LatLon(52.5200, 13.4050), zoom = 11f),
            tiles = remember { MapState<ImageBitmap>(zoomRange = 0..19) },
            onZoomIn = {},
            onZoomOut = {}
        )
    }
}
