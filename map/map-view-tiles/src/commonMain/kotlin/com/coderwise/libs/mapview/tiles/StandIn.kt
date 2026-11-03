package com.coderwise.libs.mapview.tiles

import androidx.compose.ui.geometry.Rect
import com.coderwise.libs.mapview.MapState
import com.coderwise.libs.mapview.TileKey

/** What a slot draws: a tile's content, and the fraction of it that covers the slot. */
data class Shown<T>(val key: TileKey, val content: T, val src: Rect)

/**
 * What to show for [key]: its own tile if it has arrived, otherwise the nearest filled ancestor
 * magnified, because a blurry map beats a hole. Null when neither is here yet — a placeholder's
 * cue. Both are snapshot reads, so the caller recomposes when either lands and no one else does.
 */
fun <T> MapState<T>.shown(key: TileKey, levels: Int = STAND_IN_LEVELS): Shown<T>? {
    slot(key)?.let { return Shown(key, it, WHOLE_TILE) }

    for (up in 1..levels) {
        if (key.z - up < 0) break
        val ancestorKey = TileKey(key.z - up, key.x shr up, key.y shr up)
        val ancestor = slot(ancestorKey) ?: continue
        val span = 1 shl up
        val fraction = 1f / span
        val left = (key.x and (span - 1)) * fraction
        val top = (key.y and (span - 1)) * fraction
        return Shown(ancestorKey, ancestor, Rect(left, top, left + fraction, top + fraction))
    }
    return null
}

private const val STAND_IN_LEVELS = 5
private val WHOLE_TILE = Rect(0f, 0f, 1f, 1f)
