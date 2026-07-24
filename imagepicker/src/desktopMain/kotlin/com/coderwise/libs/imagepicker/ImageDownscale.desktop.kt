package com.coderwise.libs.imagepicker

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal actual fun downscaleImageBytes(bytes: ByteArray): ByteArray = runCatching {
    val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return bytes
    val longEdge = maxOf(source.width, source.height)
    if (longEdge <= MAX_IMAGE_DIMENSION_PX) return bytes

    val scale = MAX_IMAGE_DIMENSION_PX.toDouble() / longEdge
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)

    val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    scaled.createGraphics().apply {
        drawImage(source.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null)
        dispose()
    }

    ByteArrayOutputStream().use { out ->
        ImageIO.write(scaled, "jpg", out)
        out.toByteArray()
    }
}.getOrDefault(bytes)
