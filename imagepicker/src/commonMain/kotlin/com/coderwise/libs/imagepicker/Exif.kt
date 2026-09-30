package com.coderwise.libs.imagepicker

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Instant

private const val UPRIGHT = 1
private val ValidOrientations = 1..8

private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val SHORT_BITS = 16

private const val JPEG_START = 0xFFD8
private const val MARKER_PREFIX = 0xFF
private const val APP1 = 0xE1
private const val START_OF_SCAN = 0xDA
private const val SOI_LENGTH = 2
private const val SEGMENT_HEADER = 4
private const val MARKER_LENGTH = 2
private const val LENGTH_FIELD_AT = 2

private const val MOTOROLA = 0x4D4D
private const val INTEL = 0x4949
private const val IFD_OFFSET_AT = 4
private const val IFD_COUNT_SIZE = 2
private const val IFD_ENTRY_SIZE = 12
private const val MAX_IFD_ENTRIES = 1024
private const val ENTRY_TYPE_AT = 2
private const val ENTRY_COUNT_AT = 4
private const val ENTRY_VALUE_AT = 8
private const val INLINE_VALUE_SIZE = 4

private const val TAG_ORIENTATION = 0x0112
private const val TAG_EXIF_IFD = 0x8769
private const val TAG_DATE_TIME_ORIGINAL = 0x9003
private const val TAG_OFFSET_TIME_ORIGINAL = 0x9011
private const val TYPE_SHORT = 3
private const val MAX_TEXT = 64

private const val DATE_LENGTH = 19
private val ExifHeader = "Exif".encodeToByteArray() + byteArrayOf(0, 0)

/**
 * How the camera says a picture must be turned to be upright: the EXIF orientation of a JPEG, 1 to
 * 8, or 1 (as is) when there is none or [bytes] are not a JPEG. The platform decoders for the
 * pictures this library scales ignore the tag, so whatever re-encodes the picture has to apply it.
 *
 * Reads never go out of bounds: past the end every byte reads as zero, which matches nothing, so a
 * truncated or hostile file comes out as "upright" rather than as a crash.
 */
internal fun exifOrientation(bytes: ByteArray): Int = Exif.of(bytes)?.orientation() ?: UPRIGHT

/**
 * When a JPEG says it was taken (its EXIF `DateTimeOriginal`), or null when it does not, or says
 * something that is not a date (cameras without a clock write zeros). With no zone in the file the
 * device's current one is assumed; see [PickedImage.capturedAt].
 */
internal fun exifCapturedAt(bytes: ByteArray): Instant? = Exif.of(bytes)?.capturedAt()

/** The EXIF block of a JPEG, read in place. [tiff] is where its TIFF data starts: every offset counts from there. */
private class Exif(private val bytes: ByteArray, private val tiff: Int, private val bigEndian: Boolean) {

    fun orientation(): Int {
        val entry = entry(tiff + u32(tiff + IFD_OFFSET_AT), TAG_ORIENTATION)
            ?.takeIf { u16(it + ENTRY_TYPE_AT) == TYPE_SHORT }
        return entry?.let { u16(it + ENTRY_VALUE_AT) }?.takeIf { it in ValidOrientations } ?: UPRIGHT
    }

    fun capturedAt(): Instant? {
        val exifIfd = entry(tiff + u32(tiff + IFD_OFFSET_AT), TAG_EXIF_IFD)
            ?.let { tiff + u32(it + ENTRY_VALUE_AT) }
            ?: return null
        val date = text(entry(exifIfd, TAG_DATE_TIME_ORIGINAL)) ?: return null
        val offset = text(entry(exifIfd, TAG_OFFSET_TIME_ORIGINAL))
        return instantOf(date, offset)
    }

    /** Where the entry for [tag] starts in the IFD at [ifd], or null if it has none. */
    private fun entry(ifd: Int, tag: Int): Int? {
        val count = minOf(u16(ifd), MAX_IFD_ENTRIES)
        return (0 until count)
            .map { ifd + IFD_COUNT_SIZE + it * IFD_ENTRY_SIZE }
            .firstOrNull { u16(it) == tag }
    }

    /** The text of an ASCII entry, without its terminating zero, or null if there is no entry or no text. */
    private fun text(entry: Int?): String? {
        entry ?: return null
        val length = u32(entry + ENTRY_COUNT_AT) - 1
        if (length !in 1..MAX_TEXT) return null
        val at = if (length + 1 <= INLINE_VALUE_SIZE) entry + ENTRY_VALUE_AT else tiff + u32(entry + ENTRY_VALUE_AT)
        return (0 until length).map { u8(at + it).toChar() }.joinToString("")
    }

    private fun u8(at: Int): Int = bytes.getOrNull(at)?.toInt()?.and(BYTE_MASK) ?: 0

    private fun u16(at: Int): Int =
        if (bigEndian) (u8(at) shl BYTE_BITS) or u8(at + 1) else (u8(at + 1) shl BYTE_BITS) or u8(at)

    private fun u32(at: Int): Int =
        if (bigEndian) (u16(at) shl SHORT_BITS) or u16(at + 2) else (u16(at + 2) shl SHORT_BITS) or u16(at)

    companion object {
        fun of(bytes: ByteArray): Exif? {
            val isJpeg = bytes.u16(0) == JPEG_START
            val tiff = if (isJpeg) findExifPayload(bytes, SOI_LENGTH) else null
            val byteOrder = tiff?.let { bytes.u16(it) }
            return when (byteOrder) {
                MOTOROLA -> Exif(bytes, tiff, bigEndian = true)
                INTEL -> Exif(bytes, tiff, bigEndian = false)
                else -> null
            }
        }
    }
}

/** Where the TIFF data of the Exif block starts, walking the segments ahead of the image. */
private tailrec fun findExifPayload(bytes: ByteArray, at: Int): Int? {
    val marker = bytes.u8(at + 1).takeIf { bytes.u8(at) == MARKER_PREFIX && at + SEGMENT_HEADER <= bytes.size }
    val payload = at + SEGMENT_HEADER
    return when {
        marker == null || marker == START_OF_SCAN -> null
        marker == APP1 && bytes.startsWith(ExifHeader, payload) -> payload + ExifHeader.size
        else -> findExifPayload(bytes, at + MARKER_LENGTH + bytes.u16(at + LENGTH_FIELD_AT))
    }
}

/** "2026:09:30 13:38:12", with an optional "+02:00", as a moment; null if it is not one. */
internal fun instantOf(date: String, offset: String?): Instant? = runCatching {
    require(date.length == DATE_LENGTH)
    val at = LocalDateTime(
        year = date.substring(0, 4).toInt(),
        month = date.substring(5, 7).toInt(),
        day = date.substring(8, 10).toInt(),
        hour = date.substring(11, 13).toInt(),
        minute = date.substring(14, 16).toInt(),
        second = date.substring(17, 19).toInt(),
    )
    val stated = offset?.let { runCatching { UtcOffset.parse(it) }.getOrNull() }
    if (stated != null) at.toInstant(stated) else at.toInstant(deviceZone())
}.getOrNull()

private fun deviceZone(): TimeZone = runCatching { TimeZone.currentSystemDefault() }.getOrDefault(TimeZone.UTC)

private fun ByteArray.u8(at: Int): Int = getOrNull(at)?.toInt()?.and(BYTE_MASK) ?: 0

private fun ByteArray.u16(at: Int): Int = (u8(at) shl BYTE_BITS) or u8(at + 1)

private fun ByteArray.startsWith(prefix: ByteArray, at: Int): Boolean =
    prefix.indices.all { getOrNull(at + it) == prefix[it] }
