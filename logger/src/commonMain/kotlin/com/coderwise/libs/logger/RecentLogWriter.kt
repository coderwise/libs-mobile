package com.coderwise.libs.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Keeps the last [capacity] log lines in memory so the app can read its own log
 * back — see [AppLogger.enableRecentLines].
 *
 * The ring is a plain array written without a lock. Kermit logs from whatever
 * thread the call site is on, so two lines landing at once can overwrite each
 * other in a slot or leave a hole — a line lost from a diagnostic tail is worth
 * far less than a lock on every log call, and an array write can neither corrupt
 * the buffer nor throw into the logging path.
 */
internal class RecentLogWriter(private val capacity: Int) : LogWriter() {

    private val lines = arrayOfNulls<String>(capacity)
    private var nextIndex = 0

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val line = formatLogLine(severity, message, tag, throwable)
        val slot = nextIndex
        nextIndex = (slot + 1) % capacity
        lines[slot] = line
    }

    /** The retained lines, oldest first. */
    fun snapshot(): List<String> {
        val start = nextIndex
        // The slot after the write head holds the oldest retained line once the
        // ring has wrapped, and null until it has.
        return (0 until capacity).mapNotNull { lines[(start + it) % capacity] }
    }
}
