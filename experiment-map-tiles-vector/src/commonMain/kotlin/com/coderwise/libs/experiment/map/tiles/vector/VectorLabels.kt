package com.coderwise.libs.experiment.map.tiles.vector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The place names of one tile: ordinary text composables, laid out over the tile that owns them.
 *
 * A label is a marker with words, so it is text rather than a drawing, and what text already knows
 * — fonts, a modifier of its own, being something you can click — comes with it. Give it a layer
 * of its own, over the one that drew the ground: names go over every tile rather than inside one,
 * because a name sits where its town is, which is as often as not across a seam.
 *
 * A name is placed by the one tile whose box holds its point, so nothing is placed twice, and it
 * is free to hang past that box's edges — which is the whole reason it is not part of the slot.
 * Two names of the same tile never overlap, the bigger place winning; two names of *different*
 * tiles still can, which is the honest limit of laying them out a tile at a time.
 */
@Composable
fun VectorLabels(tile: VectorTile, src: Rect, modifier: Modifier = Modifier) {
    // Only the names this tile owns: its neighbours place their own.
    val mine = tile.labels.filter {
        it.x >= src.left && it.x < src.right && it.y >= src.top && it.y < src.bottom
    }
    val apart = with(LocalDensity.current) { 3.dp.toPx() }

    Layout(
        modifier = modifier,
        content = { mine.forEach { label -> key(label.text) { Name(label, tile.ink, tile.halo) } } }
    ) { measurables, constraints ->
        val side = constraints.maxWidth / src.width // the whole tile, in pixels
        val names = measurables.map { it.measure(Constraints()) } // as wide as the words need
        val taken = mutableListOf<Rect>()

        layout(constraints.maxWidth, constraints.maxHeight) {
            names.forEachIndexed { index, name ->
                val label = mine[index]
                // A road name is only worth placing where its road runs straight for that long.
                if (name.width > label.room * side) return@forEachIndexed

                val left = (label.x - src.left) * side - name.width / 2f
                val top = (label.y - src.top) * side - name.height / 2f
                // What it covers once turned: the text turns about its middle, the box does not.
                val box = turned(label.turn, name.width.toFloat(), name.height.toFloat())
                    .translate(left + name.width / 2f, top + name.height / 2f)
                // Biggest place first, so the name that gives way is the lesser one.
                if (taken.any { it.overlaps(box) }) return@forEachIndexed
                taken += box.inflate(apart)
                name.place(left.roundToInt(), top.roundToInt())
            }
        }
    }
}

/** What a turned box covers, centred on nothing: the caller moves it onto the label. */
private fun turned(degrees: Float, width: Float, height: Float): Rect {
    val radians = degrees * PI.toFloat() / 180f
    val across = abs(width * cos(radians)) + abs(height * sin(radians))
    val down = abs(width * sin(radians)) + abs(height * cos(radians))
    return Rect(-across / 2f, -down / 2f, across / 2f, down / 2f)
}

/** The name over a white halo, which is what keeps it readable over a road or a wood. */
@Composable
private fun Name(label: Label, ink: Color, halo: Color) {
    val outline = with(LocalDensity.current) { 2.dp.toPx() }
    val style = TextStyle(fontSize = label.size.sp, fontWeight = FontWeight.Medium)
    // A road name is turned to lie along its road; graphicsLayer turns it about its own middle,
    // which is the point it was placed on.
    Box(Modifier.graphicsLayer { rotationZ = label.turn }) {
        BasicText(label.text, style = style.copy(color = halo, drawStyle = Stroke(outline)), softWrap = false)
        BasicText(label.text, style = style.copy(color = ink), softWrap = false)
    }
}

/**
 * Where a label goes, as a fraction of its tile, and what it says. [turn] is the angle it is read
 * at, degrees clockwise; [room] is how much straight road it has to sit on, as a fraction of the
 * tile, which a place has as much of as it likes.
 */
internal class Label(
    val x: Float,
    val y: Float,
    val text: String,
    val size: Float,
    val turn: Float = 0f,
    val room: Float = Float.POSITIVE_INFINITY
)
