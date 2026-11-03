package com.coderwise.libs.mapview

import androidx.compose.runtime.Immutable

/**
 * One tile of the XYZ scheme: column [x] and row [y] at level [z], rows counting from the top.
 *
 * Here rather than in `:map-view` so that whatever handles tiles without drawing them — a cache, a
 * downloader — can name one without taking Compose, and Skiko with it, along. The package stays
 * `com.coderwise.libs.mapview`, and `:map-view` gives this out as its own, so nothing that imports it
 * changes.
 */
@Immutable
data class TileKey(val z: Int, val x: Int, val y: Int)
