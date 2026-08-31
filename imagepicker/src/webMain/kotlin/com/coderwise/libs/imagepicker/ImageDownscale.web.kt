package com.coderwise.libs.imagepicker

/**
 * No-op on Web: browser image decoding/encoding (canvas / createImageBitmap) is asynchronous and
 * can't satisfy this synchronous contract. The size cap exists to keep bytes under SQLite's
 * `CursorWindow` row limit on Android, which does not apply to the JS target.
 */
internal actual fun downscaleImageBytes(bytes: ByteArray): ByteArray = bytes
