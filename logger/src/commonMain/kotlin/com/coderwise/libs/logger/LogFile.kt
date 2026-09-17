package com.coderwise.libs.logger

/**
 * An append-only text file holding log lines across a process death.
 *
 * A tail held in memory is emptied when the process is reclaimed, which is
 * exactly what happens to a phone app during a long drive or a long background
 * session: the run worth investigating is the one whose log the relaunch threw
 * away. So a line has to be on disk by the time it returns — every append is
 * flushed, rather than buffered until a close that a killed process never
 * reaches.
 */
internal interface LogFile {

    /** Appends one line, newline-terminated, flushing before it returns. */
    fun append(line: String)

    /** Every retained line, oldest first: the rotated generation, then the current one. */
    fun lines(): List<String>
}

/**
 * Opens `<[directory]>/<[fileName]>`, rotating it to `<fileName>.1` once it
 * passes [maxBytes], so the directory holds at most two generations and never
 * more than twice the cap.
 *
 * [directory] comes from the caller rather than being resolved here because
 * only the app knows it: on Android it is the `Context` the `Application`
 * already has, and reaching for one from inside a logging library — before the
 * app has started its own DI — is a startup-order trap.
 *
 * Returns null where there is no filesystem worth writing to, leaving the
 * caller whatever it keeps in memory.
 */
internal expect fun openLogFile(
    directory: String,
    fileName: String,
    maxBytes: Long,
): LogFile?
