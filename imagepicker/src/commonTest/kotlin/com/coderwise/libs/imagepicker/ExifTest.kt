package com.coderwise.libs.imagepicker

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ExifTest {

    @Test
    fun `the orientation is read in either byte order`() {
        for (bigEndian in listOf(false, true)) {
            for (orientation in 1..8) {
                val jpeg = jpegWith(exifSegment(bigEndian, orientation = orientation))

                assertEquals(orientation, exifOrientation(jpeg), "byte order big=$bigEndian")
            }
        }
    }

    @Test
    fun `no orientation or no EXIF or not a JPEG or a cut off file all read as upright`() {
        assertEquals(1, exifOrientation(jpegWith(exifSegment(date = "2026:09:30 13:38:12"))))
        assertEquals(1, exifOrientation(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())))
        assertEquals(1, exifOrientation("not an image".encodeToByteArray()))
        assertEquals(1, exifOrientation(ByteArray(0)))
        val whole = jpegWith(exifSegment(orientation = 6))
        for (cut in 0 until whole.size) assertEquals(true, exifOrientation(whole.copyOf(cut)) in 1..8)
    }

    @Test
    fun `an orientation outside one to eight reads as upright`() {
        assertEquals(1, exifOrientation(jpegWith(exifSegment(orientation = 9))))
        assertEquals(1, exifOrientation(jpegWith(exifSegment(orientation = 0))))
    }

    @Test
    fun `a stated offset fixes the moment`() {
        for (bigEndian in listOf(false, true)) {
            val jpeg = jpegWith(exifSegment(bigEndian, date = "2026:09:30 13:38:12", offset = "+02:00"))

            assertEquals(Instant.parse("2026-09-30T11:38:12Z"), exifCapturedAt(jpeg))
        }
    }

    @Test
    fun `a date with no offset is taken in the device zone`() {
        val jpeg = jpegWith(exifSegment(orientation = 6, date = "2026:09:30 13:38:12"))

        val expected = LocalDateTime(2026, 9, 30, 13, 38, 12).toInstant(TimeZone.currentSystemDefault())
        assertEquals(expected, exifCapturedAt(jpeg))
    }

    @Test
    fun `a date that is not one reads as no date`() {
        assertNull(exifCapturedAt(jpegWith(exifSegment(date = "0000:00:00 00:00:00"))))
        assertNull(exifCapturedAt(jpegWith(exifSegment(date = "2026:13:45 25:61:61"))))
        assertNull(exifCapturedAt(jpegWith(exifSegment(date = "yesterday at noon"))))
        assertNull(exifCapturedAt(jpegWith(exifSegment(orientation = 6))))
        assertNull(exifCapturedAt("not an image".encodeToByteArray()))
    }

    @Test
    fun `an offset that is not one falls back to the device zone`() {
        val jpeg = jpegWith(exifSegment(date = "2026:09:30 13:38:12", offset = "sideways"))

        val expected = LocalDateTime(2026, 9, 30, 13, 38, 12).toInstant(TimeZone.currentSystemDefault())
        assertEquals(expected, exifCapturedAt(jpeg))
    }

    @Test
    fun `the readers never go out of bounds on damaged files`() {
        val whole = jpegWith(exifSegment(orientation = 6, date = "2026:09:30 13:38:12", offset = "+02:00"))
        for (cut in 0 until whole.size) exifCapturedAt(whole.copyOf(cut))
        for (at in whole.indices) {
            val damaged = whole.copyOf().also { it[at] = 0xFF.toByte() }
            exifOrientation(damaged)
            exifCapturedAt(damaged)
        }
    }
}
