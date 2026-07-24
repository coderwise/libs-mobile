package com.coderwise.libs.imagepicker

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImageDownscaleTest {

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) for (y in 0 until height) {
            image.setRGB(x, y, ((x * 31 + y * 17) and 0xFFFFFF))
        }
        return ByteArrayOutputStream().use { ImageIO.write(image, "png", it); it.toByteArray() }
    }

    private fun ByteArray.dimensions(): Pair<Int, Int> =
        ImageIO.read(ByteArrayInputStream(this)).let { it.width to it.height }

    @Test
    fun `caps the long edge and shrinks oversized images`() {
        val original = pngBytes(4000, 3000)

        val result = downscaleImageBytes(original)

        val (w, h) = result.dimensions()
        assertEquals(MAX_IMAGE_DIMENSION_PX, maxOf(w, h), "long edge should be capped")
        assertEquals(960, h, "aspect ratio (4:3) should be preserved")
        assertTrue(result.size < original.size, "re-encoded bytes should be smaller")
    }

    @Test
    fun `leaves already-small images untouched`() {
        val small = pngBytes(800, 600)

        val result = downscaleImageBytes(small)

        assertSame(small, result, "small images should be returned as-is, not re-encoded")
    }
}
