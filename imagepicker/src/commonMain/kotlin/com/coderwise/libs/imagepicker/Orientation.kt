package com.coderwise.libs.imagepicker

private const val RIGHT_ANGLE = 90f
private const val STRAIGHT = 180f
private const val THREE_QUARTERS = 270f

/**
 * What to do to the pixels as stored to show them upright: mirror first, then turn clockwise.
 * That is the order the EXIF orientation tag is defined in.
 */
internal data class Orientation(val degrees: Float, val mirrored: Boolean) {
    /** Whether the picture ends up with its width and height swapped. */
    val swapsAxes: Boolean get() = degrees == RIGHT_ANGLE || degrees == THREE_QUARTERS

    val isUpright: Boolean get() = degrees == 0f && !mirrored

    companion object {
        val Upright = Orientation(0f, mirrored = false)

        /** Indexed by EXIF value minus one: 1 to 8, in the order the tag defines them. */
        private val ByExifValue = listOf(
            Upright,
            Orientation(0f, mirrored = true),
            Orientation(STRAIGHT, mirrored = false),
            Orientation(STRAIGHT, mirrored = true),
            Orientation(THREE_QUARTERS, mirrored = true),
            Orientation(RIGHT_ANGLE, mirrored = false),
            Orientation(RIGHT_ANGLE, mirrored = true),
            Orientation(THREE_QUARTERS, mirrored = false),
        )

        fun of(exif: Int): Orientation = ByExifValue.getOrElse(exif - 1) { Upright }
    }
}
