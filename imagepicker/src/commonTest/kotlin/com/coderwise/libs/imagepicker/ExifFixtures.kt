package com.coderwise.libs.imagepicker

private const val TIFF_MAGIC = 42
private const val ENTRY_SIZE = 12
private const val ASCII = 2
private const val SHORT = 3
private const val LONG = 4

/** A little- or big-endian byte writer, for building EXIF by hand. */
private class Bytes(private val bigEndian: Boolean) {
    private val out = mutableListOf<Byte>()
    val size get() = out.size

    fun u8(value: Int) = apply { out += value.toByte() }
    fun u16(value: Int) = apply {
        if (bigEndian) { u8(value shr 8); u8(value) } else { u8(value); u8(value shr 8) }
    }
    fun u32(value: Int) = apply {
        if (bigEndian) { u16(value shr 16); u16(value) } else { u16(value); u16(value shr 16) }
    }
    fun ascii(text: String) = apply { text.forEach { u8(it.code) }; u8(0) }
    fun toByteArray() = out.toByteArray()
}

/**
 * The APP1 segment of a JPEG holding EXIF with whichever of [orientation], [date] (`DateTimeOriginal`)
 * and [offset] (`OffsetTimeOriginal`) are given. With a date or offset there is an Exif IFD; with none
 * of them the orientation is all there is.
 */
internal fun exifSegment(
    bigEndian: Boolean = false,
    orientation: Int? = null,
    date: String? = null,
    offset: String? = null,
): ByteArray {
    val hasExifIfd = date != null || offset != null
    val ifd0Entries = listOfNotNull(orientation?.let { 0x0112 }, if (hasExifIfd) 0x8769 else null)
    val exifEntries = listOfNotNull(date?.let { 0x9003 }, offset?.let { 0x9011 })

    val ifd0At = 8
    val ifd0Size = 2 + ifd0Entries.size * ENTRY_SIZE + 4
    val exifIfdAt = ifd0At + ifd0Size
    val exifIfdSize = if (hasExifIfd) 2 + exifEntries.size * ENTRY_SIZE + 4 else 0
    val dateAt = exifIfdAt + exifIfdSize
    val offsetAt = dateAt + (date?.let { it.length + 1 } ?: 0)

    val tiff = Bytes(bigEndian)
    tiff.u16(if (bigEndian) 0x4D4D else 0x4949).u16(TIFF_MAGIC).u32(ifd0At)
    tiff.u16(ifd0Entries.size)
    ifd0Entries.forEach { tag ->
        if (tag == 0x0112) {
            tiff.u16(tag).u16(SHORT).u32(1).u16(orientation!!).u16(0)
        } else {
            tiff.u16(tag).u16(LONG).u32(1).u32(exifIfdAt)
        }
    }
    tiff.u32(0)
    if (hasExifIfd) {
        tiff.u16(exifEntries.size)
        exifEntries.forEach { tag ->
            if (tag == 0x9003) {
                tiff.u16(tag).u16(ASCII).u32(date!!.length + 1).u32(dateAt)
            } else {
                tiff.u16(tag).u16(ASCII).u32(offset!!.length + 1).u32(offsetAt)
            }
        }
        tiff.u32(0)
        date?.let { tiff.ascii(it) }
        offset?.let { tiff.ascii(it) }
    }

    val payload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff.toByteArray()
    val length = payload.size + 2
    return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
}

/** A JPEG with nothing in it but [segment]: enough for the EXIF reader, not for a decoder. */
internal fun jpegWith(segment: ByteArray): ByteArray =
    byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + segment + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

/** [jpeg] with [segment] put ahead of everything else in it, so a decoder still reads the picture. */
internal fun ByteArray.withExifSegment(segment: ByteArray): ByteArray =
    copyOfRange(0, 2) + segment + copyOfRange(2, size)
