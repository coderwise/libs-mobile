package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.map.TiledMapState
import kotlinx.coroutines.launch

@Composable
internal fun CameraReadout(state: TiledMapState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp
    ) {
        Text(
            text = "lat ${state.latitude.formatted()}  lon ${state.longitude.formatted()}  z ${state.zoom.formatted(2)}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/**
 * The zoom buttons, and — where a [zoom] is given — what the camera is actually at.
 *
 * Worth showing on a map you can pinch: a button steps by a whole level, a pinch lands between
 * them, and which one you are on is what decides whether a tile is its own or a magnified
 * ancestor, and how wide the roads on it are drawn.
 */
@Composable
internal fun ZoomControls(
    modifier: Modifier = Modifier,
    zoom: Float? = null,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        zoom?.let { ZoomReadout(it) }
        FilledTonalButton(onClick = onZoomIn) { Text("+") }
        FilledTonalButton(onClick = onZoomOut) { Text("−") }
    }
}

/** What zoom the camera is at, to one decimal — a pinch sits between the levels a button steps by. */
@Composable
internal fun ZoomReadout(zoom: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp
    ) {
        Text(
            text = "z ${zoom.toDouble().formatted(1)}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun ZoomControls(state: TiledMapState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    ZoomControls(
        modifier = modifier,
        onZoomIn = { scope.launch { state.zoomIn() } },
        onZoomOut = { scope.launch { state.zoomOut() } }
    )
}
