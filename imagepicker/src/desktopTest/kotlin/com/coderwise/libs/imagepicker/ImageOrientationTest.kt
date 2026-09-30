package com.coderwise.libs.imagepicker

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/** What happens to a picture's orientation and date when it is scaled. */
class ImageOrientationTest {

    /** A 16x16 JPEG, red on its left half and blue on its right, with no EXIF of its own. */
    private val redLeftBlueRight: ByteArray = run {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 16) for (y in 0 until 16) image.setRGB(x, y, if (x < 8) 0xFF0000 else 0x0000FF)
        ByteArrayOutputStream().use { ImageIO.write(image, "jpg", it); it.toByteArray() }
    }

    private fun fixture(orientation: Int, date: String? = null) =
        redLeftBlueRight.withExifSegment(exifSegment(orientation = orientation, date = date, offset = date?.let { "+00:00" }))

    private fun ByteArray.pixel(x: Int, y: Int): Triple<Int, Int, Int> {
        val rgb = ImageIO.read(ByteArrayInputStream(this)).getRGB(x, y)
        return Triple((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    }

    private fun Triple<Int, Int, Int>.isRed() = first > 150 && third < 100
    private fun Triple<Int, Int, Int>.isBlue() = third > 150 && first < 100

    @Test
    fun `the fixture decodes and is red on the left`() {
        assertTrue(redLeftBlueRight.pixel(2, 8).isRed())
        assertTrue(redLeftBlueRight.pixel(13, 8).isBlue())
    }

    @Test
    fun `a picture tagged as turned clockwise comes out upright when scaled`() {
        val result = downscaleImageBytes(fixture(orientation = 6), maxDimensionPx = 8)

        val image = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(8, image.width)
        assertEquals(8, image.height)
        // Stored left/right, upright it is top/bottom: turning the stored left edge clockwise puts it on top.
        assertTrue(result.pixel(4, 1).isRed(), "the stored left half should end up on top")
        assertTrue(result.pixel(4, 6).isBlue(), "the stored right half should end up at the bottom")
    }

    @Test
    fun `a picture tagged as turned the other way comes out upright too`() {
        val result = downscaleImageBytes(fixture(orientation = 8), maxDimensionPx = 8)

        assertTrue(result.pixel(4, 1).isBlue(), "the stored right half should end up on top")
        assertTrue(result.pixel(4, 6).isRed(), "the stored left half should end up at the bottom")
    }

    @Test
    fun `a mirrored picture comes out mirrored back`() {
        val result = downscaleImageBytes(fixture(orientation = 2), maxDimensionPx = 8)

        assertTrue(result.pixel(1, 4).isBlue(), "mirrored, the stored left half ends up on the right")
        assertTrue(result.pixel(6, 4).isRed())
    }

    @Test
    fun `an upright picture is only scaled`() {
        val result = downscaleImageBytes(fixture(orientation = 1), maxDimensionPx = 8)

        assertTrue(result.pixel(1, 4).isRed())
        assertTrue(result.pixel(6, 4).isBlue())
    }

    @Test
    fun `a picture small enough is handed back untouched with its tag`() {
        val original = fixture(orientation = 6)

        assertSame(original, downscaleImageBytes(original, maxDimensionPx = 100))
    }

    @Test
    fun `the date comes from the original even though scaling drops it`() {
        val original = fixture(orientation = 6, date = "2026:09:30 13:38:12")

        val picked = pickedImage(original, maxDimensionPx = 8)

        assertEquals(Instant.parse("2026-09-30T13:38:12Z"), picked.capturedAt)
        assertEquals(1, exifOrientation(picked.bytes), "the copy carries no tag: it is already upright")
        assertNull(exifCapturedAt(picked.bytes))
    }

    @Test
    fun `a full size pick keeps the file as it is and still reports its date`() {
        val original = fixture(orientation = 6, date = "2026:09:30 13:38:12")

        val picked = pickedImage(original, maxDimensionPx = null)

        assertSame(original, picked.bytes)
        assertNotNull(picked.capturedAt)
    }
}
