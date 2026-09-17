package com.coderwise.libs.logger

import co.touchlab.kermit.Severity
import kotlin.time.Clock

/**
 * One log line as this library renders it, shared by every writer that keeps
 * lines for later — so a log read back out of memory and one read out of a file
 * are the same text, and nobody reading one has to know which they were sent.
 */
internal fun formatLogLine(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable?,
): String = buildString {
    append(Clock.System.now())
    append(' ')
    append(severity.name.first())
    append('/')
    append(tag)
    append(": ")
    append(message)
    if (throwable != null) {
        append(" | ")
        append(throwable.stackTraceToString())
    }
}
