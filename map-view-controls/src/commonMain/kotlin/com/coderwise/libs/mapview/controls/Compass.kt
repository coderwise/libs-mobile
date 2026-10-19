package com.coderwise.libs.mapview.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderwise.libs.mapview.MapCameraState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A needle that points north wherever the map has been turned, and puts it back when pressed.
 *
 * Only there while the map is turned: a map that is north-up has nothing to say about north. Pass
 * [alwaysVisible] for one that should stay put.
 */
@Composable
fun NorthButton(
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    alwaysVisible: Boolean = false,
    background: Color = Color.White
) {
    if (!alwaysVisible && abs(shortestTurn(camera.bearing, 0f)) < NORTH_TOLERANCE) return
    val scope = rememberCoroutineScope()

    Canvas(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button) { scope.launch { camera.turnTo(0f) } }
            .semantics { contentDescription = "North" }
    ) {
        // The map turned by the bearing puts north that far the other way round from the top.
        rotate(-camera.bearing) {
            val middle = Offset(size.width / 2, size.height / 2)
            val reach = size.minDimension * 0.32f
            val half = reach * 0.45f
            drawPath(
                Path().apply {
                    moveTo(middle.x, middle.y - reach)
                    lineTo(middle.x - half, middle.y)
                    lineTo(middle.x + half, middle.y)
                    close()
                },
                Color(0xFFD32F2F)
            )
            drawPath(
                Path().apply {
                    moveTo(middle.x, middle.y + reach)
                    lineTo(middle.x - half, middle.y)
                    lineTo(middle.x + half, middle.y)
                    close()
                },
                Color(0xFF9E9E9E)
            )
        }
    }
}

/** A map this close to north-up is north-up. */
private const val NORTH_TOLERANCE = 0.5f

/**
 * A strip of compass headings with the one the top of the map is facing in the middle: ticks every
 * five degrees, a number every thirty and the points of the compass every forty-five. It slides as
 * the map turns.
 *
 * [span] is how many degrees the strip shows. Give it an [onClick] for the usual thing — back to
 * north — which is `camera.turnTo(0f)`.
 */
@Composable
fun CompassTape(
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    span: Float = 120f,
    onClick: (() -> Unit)? = null,
    color: Color = Color.Black,
    background: Color = Color.White.copy(alpha = 0.85f),
    textStyle: TextStyle = TextStyle(fontSize = 11.sp)
) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val tape = modifier
        .fillMaxWidth()
        .height(36.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(background)
        .semantics { contentDescription = "Compass" }

    Canvas(if (onClick == null) tape else tape.clickable(role = Role.Button, onClick = onClick)) {
        compassTicks(camera.bearing, span).forEach { tick ->
            val x = size.width * (0.5f + tick.offset)
            val reach = when (tick.kind) {
                TickKind.Minor -> 6.dp
                TickKind.Major -> 10.dp
                TickKind.Cardinal -> 14.dp
            }.toPx()
            drawLine(
                color,
                Offset(x, size.height),
                Offset(x, size.height - reach),
                strokeWidth = if (tick.kind == TickKind.Cardinal) 2.dp.toPx() else 1.dp.toPx()
            )
            tick.label?.let {
                val text = measurer.measure(it, textStyle.copy(color = color))
                drawText(text, topLeft = Offset(x - text.size.width / 2f, 2.dp.toPx()))
            }
        }
        // Where the top of the map is facing.
        val middle = size.width / 2
        drawPath(
            Path().apply {
                moveTo(middle - 5.dp.toPx(), 0f)
                lineTo(middle + 5.dp.toPx(), 0f)
                lineTo(middle, 6.dp.toPx())
                close()
            },
            Color(0xFFD32F2F)
        )
    }
}

internal enum class TickKind { Minor, Major, Cardinal }

/**
 * One mark on the tape: how far from the middle it is as a fraction of the strip, in `-0.5..0.5`,
 * and what it says, if anything.
 */
internal class CompassTick(val offset: Float, val heading: Int, val kind: TickKind, val label: String?)

/** The marks to draw for a strip [span] degrees wide, centred on [bearing]. */
internal fun compassTicks(bearing: Float, span: Float, step: Int = 5): List<CompassTick> {
    val low = bearing - span / 2
    val high = bearing + span / 2
    val ticks = mutableListOf<CompassTick>()
    var at = ceil(low / step).toInt() * step
    while (at <= high) {
        val heading = at.mod(360)
        val kind = when {
            heading % 45 == 0 -> TickKind.Cardinal
            heading % 15 == 0 -> TickKind.Major
            else -> TickKind.Minor
        }
        val label = when {
            heading % 45 == 0 -> POINTS[heading / 45]
            heading % 30 == 0 -> heading.toString()
            else -> null
        }
        ticks += CompassTick((at - bearing) / span, heading, kind, label)
        at += step
    }
    return ticks
}

private val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
