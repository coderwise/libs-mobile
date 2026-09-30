package com.coderwise.libs.imagepicker

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import kotlin.time.Instant

/**
 * When [bytes] say they were taken, asked of ImageIO, which reads the formats an iPhone writes
 * (HEIC) as well as JPEG. The shared reader only knows JPEG. Null when there is no date, or
 * ImageIO gives something other than the usual property dictionaries.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun imageIoCapturedAt(bytes: ByteArray): Instant? = runCatching {
    if (bytes.isEmpty()) return null
    val data = bytes.asUByteArray().usePinned { CFDataCreate(null, it.addressOf(0), bytes.size.toLong()) }
        ?: return null
    try {
        val source = CGImageSourceCreateWithData(data, null) ?: return null
        try {
            val properties = CFBridgingRelease(CGImageSourceCopyPropertiesAtIndex(source, 0u, null)) as? Map<*, *>
            val exif = properties?.get("{Exif}") as? Map<*, *>
            val date = exif?.get("DateTimeOriginal") as? String ?: return null
            instantOf(date, exif["OffsetTimeOriginal"] as? String)
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(data)
    }
}.getOrNull()
