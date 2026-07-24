package com.coderwise.libs.imagepicker

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
internal actual fun downscaleImageBytes(bytes: ByteArray): ByteArray {
    if (bytes.isEmpty()) return bytes
    val source = UIImage(data = bytes.toNSData())
    val (srcW, srcH) = source.size.useContents { width to height }
    val longEdge = max(srcW, srcH)
    if (longEdge <= MAX_IMAGE_DIMENSION_PX.toDouble()) return bytes

    val scale = MAX_IMAGE_DIMENSION_PX.toDouble() / longEdge
    val newW = srcW * scale
    val newH = srcH * scale

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(newW, newH), false, 1.0)
    source.drawInRect(CGRectMake(0.0, 0.0, newW, newH))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    val data = UIImageJPEGRepresentation(scaled ?: source, IMAGE_JPEG_QUALITY / 100.0) ?: return bytes
    return data.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
