package com.coderwise.libs.mapview.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderwise.libs.mapview.MapCameraState
import com.coderwise.libs.mapview.ZOOM_LIMITS
import com.coderwise.libs.mapview.flyTo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Zoom in and zoom out, one [step] a press, animated.
 *
 * A second press while the first is still flying carries on from where that one was going, so
 * pressing twice is two levels rather than one. A button turns itself off at the end of
 * [zoomRange] — pass the source's own, for a map that should stop where its tiles do.
 */
@Composable
fun ZoomButtons(
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    zoomRange: ClosedFloatingPointRange<Float> = ZOOM_LIMITS,
    step: Float = 1f,
    background: Color = Color.White,
    contentColor: Color = Color.Black
) {
    val scope = rememberCoroutineScope()
    val flight = remember { Flight() }

    fun zoomBy(delta: Float) {
        val target = ((flight.target ?: camera.zoom) + delta).coerceIn(zoomRange)
        flight.job?.cancel()
        flight.target = target
        flight.job = scope.launch {
            try {
                camera.flyTo(camera.center, target)
            } finally {
                // Only the flight that is still the latest one is over; a cancelled one is not.
                if (flight.target == target) flight.target = null
            }
        }
    }

    Column(modifier.clip(RoundedCornerShape(8.dp)).background(background)) {
        ControlButton("+", "Zoom in", camera.zoom < zoomRange.endInclusive, contentColor) { zoomBy(step) }
        Box(Modifier.size(44.dp, 1.dp).background(contentColor.copy(alpha = 0.2f)))
        ControlButton("−", "Zoom out", camera.zoom > zoomRange.start, contentColor) { zoomBy(-step) }
    }
}

/** Where a zoom press is headed, so the next one can start from there. */
private class Flight {
    var job: Job? = null
    var target: Float? = null
}

@Composable
private fun ControlButton(
    text: String,
    description: String,
    enabled: Boolean,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
                fontSize = 22.sp
            )
        )
    }
}

/**
 * A line of credit over the map — OpenStreetMap's licence, a tile server's terms, whatever the
 * sources in use ask for.
 *
 * Give it an [onClick] to open the page behind the credit; what opens it is the app's, because
 * this does not know what a browser is.
 */
@Composable
fun Attribution(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Color.Black,
    background: Color = Color.White.copy(alpha = 0.75f),
    textStyle: TextStyle = TextStyle(fontSize = 10.sp)
) {
    val plate = modifier.clip(RoundedCornerShape(4.dp)).background(background)
    BasicText(
        text,
        style = textStyle.copy(color = color),
        modifier = (if (onClick == null) plate else plate.clickable(role = Role.Button, onClick = onClick))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
