package com.coderwise.libs.mapview.tiles.vector

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The place names of one tile, laid out and drawn over the tile that owns them.
 *
 * Drawn, not composed. A name used to be two `BasicText`s — one for the halo, one for the fill —
 * and a tile offers up to [LABEL_BUDGET] names, so a screen of them was some fifteen hundred text
 * nodes, each laying out its own paragraph, and a second one for the halo saying the same word at
 * the same size. Measured on a z14 London pan they were three quarters of everything the app gave
 * the collector: 171 MB against 71 MB once they became drawing, with the worst GC pause down from
 * 15.5 ms to nothing at all and half again as many frames delivered. A [TextMeasurer] lays each
 * name out once and both passes draw that one layout.
 *
 * What that costs is what a text node knows: a name is no longer something with a modifier of its
 * own, and no longer something you can click. Nothing here needed either.
 *
 * Give it a layer of its own, over the one that drew the ground: names go over every tile rather
 * than inside one, because a name sits where its town is, which is as often as not across a seam.
 * Drawing rather than composing does not change that — put the two in one layer and the next
 * tile's ground paints over the half of the name that reached into it.
 *
 * A name is placed by the one tile whose box holds its point, so nothing is placed twice, and it
 * is free to draw past that box's edges — which is the whole reason it is not part of the slot.
 * Two names of the same tile never overlap, the bigger place winning; two names of *different*
 * tiles still can, which is the honest limit of laying them out a tile at a time.
 *
 * Pass the camera's [bearing] when the map can be turned. A tile layer turns as one plane, so
 * without it the names turn with the ground and are upside down past a certain angle. A place name
 * takes the bearing back out and stays level; a road name keeps following its road, and only turns
 * end for end when that is what makes it read left to right.
 */
@Composable
fun VectorLabels(
    tile: VectorTile,
    src: Rect,
    modifier: Modifier = Modifier,
    bearing: Float = 0f
) {
    // Only the names this tile owns: its neighbours place their own. And only the first
    // [LABEL_BUDGET] of those — see the constant.
    val mine = remember(tile, src) {
        tile.labels.filter {
            it.x >= src.left && it.x < src.right && it.y >= src.top && it.y < src.bottom
        }.take(LABEL_BUDGET)
    }
    val measurer = rememberTextMeasurer(cacheSize = LABEL_BUDGET)
    val density = LocalDensity.current
    val apart = with(density) { 3.dp.toPx() }
    val outline = with(density) { 2.dp.toPx() }

    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val placed = density.place(mine, measurer, src, size.width / src.width, bearing, apart)
                onDrawBehind {
                    placed.forEach { name ->
                        rotate(name.angle, pivot = name.middle) {
                            // Halo and fill off one paragraph: the same layout drawn twice, rather
                            // than two text nodes measuring the same words.
                            drawText(name.layout, tile.halo, name.topLeft, drawStyle = Stroke(outline))
                            // Fill named rather than left to default: a null drawStyle means "as
                            // the layout's style says", and the paragraph's paint still carries the
                            // Stroke from the pass above — so the fill would thicken the name into
                            // a dark blob instead of covering the halo.
                            drawText(name.layout, tile.ink, name.topLeft, drawStyle = Fill)
                        }
                    }
                }
            }
    )
}

/** One name that survived the layout: what to draw, where, and how far turned. */
private class PlacedName(
    val layout: TextLayoutResult,
    val topLeft: Offset,
    val middle: Offset,
    val angle: Float
)

/**
 * Which of [labels] fit, and where they go — the same pass the composable layout used to do, with
 * the text measured rather than composed.
 */
private fun Density.place(
    labels: List<Label>,
    measurer: TextMeasurer,
    src: Rect,
    side: Float,
    bearing: Float,
    apart: Float
): List<PlacedName> {
    val placed = mutableListOf<PlacedName>()
    val taken = mutableListOf<Rect>()

    labels.forEach { label ->
        // Laying a name out is what costs, so a name that cannot fit the run it has to sit on is
        // dropped before that rather than after.
        if (!couldFit(label, side)) return@forEach
        val layout = measurer.measure(
            label.text,
            TextStyle(fontSize = label.size.sp, fontWeight = FontWeight.Medium),
            softWrap = false
        )
        val width = layout.size.width.toFloat()
        val height = layout.size.height.toFloat()
        // A road name is only worth placing where its road runs straight for that long.
        if (width > label.room * side) return@forEach

        val left = (label.x - src.left) * side - width / 2f
        val top = (label.y - src.top) * side - height / 2f
        val middle = Offset(left + width / 2f, top + height / 2f)
        val angle = label.angle(bearing)
        // What it covers once turned: the text turns about its middle, the box does not.
        val box = turned(angle, width, height).translate(middle.x, middle.y)
        // Biggest place first, so the name that gives way is the lesser one.
        if (taken.any { it.overlaps(box) }) return@forEach
        taken += box.inflate(apart)
        placed += PlacedName(layout, Offset(left, top), middle, angle)
    }
    return placed
}

/**
 * How many of a tile's names are worth considering.
 *
 * They arrive in priority order — the biggest places first, then the road names with the most road
 * to stand on — and a name that collides with one already placed is dropped. A 256 dp square holds
 * a dozen or so before it is full, and it fills from the top of that order, so the names beyond
 * this many are ones that would lose.
 */
private const val LABEL_BUDGET = 48

/**
 * Whether [label] could fit the run it has to sit on, before a paragraph is built for it.
 *
 * The same font-free bound the decode applies, but against what the tile actually measures on
 * screen rather than the widest it is ever stretched to. NARROWEST_GLYPH is a lower bound on a
 * glyph's advance, so a name this turns away is one the real width test turns away too.
 */
internal fun Density.couldFit(label: Label, side: Float): Boolean =
    label.room * side >= NARROWEST_GLYPH * label.size.sp.toPx() * label.text.length

/** What a turned box covers, centred on nothing: the caller moves it onto the label. */
private fun turned(degrees: Float, width: Float, height: Float): Rect {
    val radians = degrees * PI.toFloat() / 180f
    val across = abs(width * cos(radians)) + abs(height * sin(radians))
    val down = abs(width * sin(radians)) + abs(height * cos(radians))
    return Rect(-across / 2f, -down / 2f, across / 2f, down / 2f)
}

/**
 * How far to turn a label *inside* a tile layer that is itself turned by -[bearing].
 *
 * A place stays level on screen, so it turns by the bearing to undo it. A road follows its road,
 * which turns with the ground, so it keeps the angle it was given — end for end when the map has
 * been turned far enough that the words would otherwise be read upside down.
 */
internal fun Label.angle(bearing: Float): Float {
    if (upright) return bearing
    val read = (turn - bearing + 180f).mod(360f) - 180f // how it lies on screen, in (-180, 180]
    return if (read > 90f || read <= -90f) turn + 180f else turn
}

/**
 * Where a label goes, as a fraction of its tile, and what it says. [turn] is the angle it lies at
 * on the ground, degrees clockwise; [room] is how much straight road it has to sit on, as a
 * fraction of the tile, which a place has as much of as it likes. [upright] is the difference
 * between the two kinds: a place name is level however the map is turned, a road name is not.
 */
@Immutable
internal class Label(
    val x: Float,
    val y: Float,
    val text: String,
    val size: Float,
    val turn: Float = 0f,
    val room: Float = Float.POSITIVE_INFINITY,
    val upright: Boolean = true
)
