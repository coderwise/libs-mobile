package com.coderwise.libs.imagepicker

import java.awt.Image
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal actual fun downscaleImageBytes(bytes: ByteArray, maxDimensionPx: Int): ByteArray = runCatching {
    val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return bytes
    val longEdge = maxOf(source.width, source.height)
    if (longEdge <= maxDimensionPx) return bytes

    val scale = maxDimensionPx.toDouble() / longEdge
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)

    val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    scaled.createGraphics().apply {
        drawImage(source.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null)
        dispose()
    }

    // ImageIO ignores the EXIF orientation and the re-encode drops it, so turn the picture here.
    val upright = scaled.turned(Orientation.of(exifOrientation(bytes)))

    ByteArrayOutputStream().use { out ->
        ImageIO.write(upright, "jpg", out)
        out.toByteArray()
    }
}.getOrDefault(bytes)

/** This picture mirrored and then turned clockwise as [orientation] says, about its middle. */
private fun BufferedImage.turned(orientation: Orientation): BufferedImage {
    if (orientation.isUpright) return this
    val result = BufferedImage(
        if (orientation.swapsAxes) height else width,
        if (orientation.swapsAxes) width else height,
        BufferedImage.TYPE_INT_RGB,
    )
    // Transforms apply to the drawn picture last-listed first: centre it, mirror it, turn it, and
    // put its middle in the middle of the result.
    val transform = AffineTransform().apply {
        translate(result.width / 2.0, result.height / 2.0)
        rotate(Math.toRadians(orientation.degrees.toDouble()))
        if (orientation.mirrored) scale(-1.0, 1.0)
        translate(-width / 2.0, -height / 2.0)
    }
    result.createGraphics().apply {
        drawImage(this@turned, transform, null)
        dispose()
    }
    return result
}
