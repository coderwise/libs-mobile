package com.coderwise.libs.mapview.geojson

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How one layer of GeoJSON looks: every feature in it, whatever its properties say. A map that wants
 * two looks has two layers — see [GeoJson] — which is also how they are put in order.
 *
 * Widths are dp, so they stay the same at every zoom. A transparent colour, or a width of 0, leaves
 * that part out.
 *
 * @param fill the inside of a polygon, holes excepted.
 * @param stroke the outline of a polygon, and a line.
 * @param strokeWidth the width of that outline and of a line.
 * @param pointColor a point is a dot of this colour.
 * @param pointRadius and this big.
 */
@Immutable
data class GeoJsonStyle(
    val fill: Color = Color(0x330066FF),
    val stroke: Color = Color(0xFF0066FF),
    val strokeWidth: Dp = 2.dp,
    val pointColor: Color = stroke,
    val pointRadius: Dp = 4.dp
)
