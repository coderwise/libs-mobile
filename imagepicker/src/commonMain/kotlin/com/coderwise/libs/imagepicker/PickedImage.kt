package com.coderwise.libs.imagepicker

import kotlin.time.Instant

/**
 * A picture the user chose: its [bytes], and when it was taken if the file says so.
 *
 * [capturedAt] is read from the file's own EXIF date (`DateTimeOriginal`) before any scaling throws
 * the metadata away, so it is there even when [bytes] are a re-encoded copy. A file with no usable
 * date (a screenshot, a PNG, a stripped export) has none. Cameras write the time as the wall clock
 * at the place, without a zone unless the phone is recent enough to add one, so when it has none
 * the device's current zone is assumed: right for photos taken where the user still is.
 *
 * Not a data class: comparing two of them would compare every byte.
 */
class PickedImage(val bytes: ByteArray, val capturedAt: Instant?)
