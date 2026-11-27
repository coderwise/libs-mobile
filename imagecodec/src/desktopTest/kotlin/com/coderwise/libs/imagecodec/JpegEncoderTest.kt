package com.coderwise.libs.imagecodec

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Runs on desktop only: encoding needs a real platform encoder, and the host tests have none. */
class JpegEncoderTest {

    private fun redSquare(size: Int) = ImageBitmap(size, size).also {
        Canvas(it).drawRect(Rect(0f, 0f, size.toFloat(), size.toFloat()), Paint().apply { color = Color.Red })
    }

    @Test
    fun `an encoded picture is a jpeg of the same size`() {
        val bytes = assertNotNull(redSquare(16).encodeToJpeg())

        // A JPEG starts with the marker FF D8.
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val decoded = Image.makeFromEncoded(bytes)
        assertEquals(16, decoded.width)
        assertEquals(16, decoded.height)
    }
}
