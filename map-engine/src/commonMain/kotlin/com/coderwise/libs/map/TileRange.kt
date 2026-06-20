package com.coderwise.libs.map

data class TileRange(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
) {
    inline fun forEach(action: (x: Int, y: Int) -> Unit) {
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                action(x, y)
            }
        }
    }
}
