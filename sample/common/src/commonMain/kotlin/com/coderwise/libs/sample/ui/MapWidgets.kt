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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.map.TiledMapState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun CameraReadout(state: TiledMapState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp
    ) {
        Text(
            text = "lat ${state.latitude.rounded()}  lon ${state.longitude.rounded()}  z ${state.zoom.rounded(2)}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun ZoomControls(state: TiledMapState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { scope.launch { state.zoomIn() } }) { Text("+") }
        FilledTonalButton(onClick = { scope.launch { state.zoomOut() } }) { Text("−") }
    }
}

/** Multiplatform-safe fixed-decimal formatting (String.format is JVM-only). */
private fun Double.rounded(decimals: Int = 4): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    return ((this * factor).roundToInt() / factor).toString()
}
