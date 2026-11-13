package com.coderwise.libs.sample.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview

/**
 * Zoom in and zoom out. What a press does is the caller's — fly the camera, step it — and so is
 * whether a button is on: pass [canZoomIn] and [canZoomOut] from `camera.zoomRange` for a map that
 * stops where its tiles do.
 */
@Composable
fun ZoomButtons(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    canZoomIn: Boolean = true,
    canZoomOut: Boolean = true,
    background: Color = Color.White,
    contentColor: Color = Color.Black
) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(background)) {
        ControlButton("+", "Zoom in", canZoomIn, contentColor, onZoomIn)
        Box(Modifier.size(44.dp, 1.dp).background(contentColor.copy(alpha = 0.2f)))
        ControlButton("−", "Zoom out", canZoomOut, contentColor, onZoomOut)
    }
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

@Preview(name = "Light")
@Composable
private fun ZoomButtonsLightPreview() {
    ZoomButtons(onZoomIn = {}, onZoomOut = {}, background = Color.White, contentColor = Color.Black)
}

@Preview(name = "Dark", uiMode = 0x20)
@Composable
private fun ZoomButtonsDarkPreview() {
    ZoomButtons(onZoomIn = {}, onZoomOut = {}, background = Color(0xFF1E1E1E), contentColor = Color.White)
}

@Preview(name = "Light")
@Composable
private fun AttributionLightPreview() {
    Attribution(text = "© OpenStreetMap contributors", color = Color.Black, background = Color.White.copy(alpha = 0.75f))
}

@Preview(name = "Dark", uiMode = 0x20)
@Composable
private fun AttributionDarkPreview() {
    Attribution(text = "© OpenStreetMap contributors", color = Color.White, background = Color(0xFF1E1E1E).copy(alpha = 0.75f))
}
