package com.coderwise.libs.logger

/**
 * No file. A browser has no directory to append to that outlives the tab, so
 * the caller is left with whatever it keeps in memory.
 */
internal actual fun openLogFile(
    directory: String,
    fileName: String,
    maxBytes: Long,
): LogFile? = null
