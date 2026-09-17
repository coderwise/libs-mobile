package com.coderwise.libs.logger

import co.touchlab.kermit.Logger

/**
 * Thin app-wide logging facade over Kermit. Centralizes logging so call sites
 * never silently swallow failures and so the backend (log writers, crash
 * reporting) can be configured in one place.
 *
 * TODO(production): route Kermit to Crashlytics on Android (a `CrashlyticsLogWriter`)
 * so non-fatal warnings/errors are captured in release builds.
 */
object AppLogger {

    private var recentWriter: RecentLogWriter? = null
    private var recentFile: LogFile? = null

    /**
     * Starts keeping the last [capacity] lines in memory, readable through
     * [recentLines].
     *
     * The other two ways off a device only cover a tethered debug build: stdout
     * lives as long as `devicectl ... --console` is attached, and the file
     * [enableDeviceVisibleLogging] writes exists only in a debug binary. The
     * runs worth investigating are neither — a TestFlight or Play build, driven
     * with the phone in a pocket. Holding the tail in the process lets the app
     * hand its own log to a share sheet, from any build type.
     *
     * In the process, though, is as long as it lasts. Pair it with
     * [enableRecentLinesFile] wherever the run can outlive the process.
     *
     * On iOS, call it *after* [enableDeviceVisibleLogging], which replaces the
     * configured writers rather than adding to them.
     *
     * Call it as early in startup as there is a startup to speak of; it *adds* a
     * writer, so whatever else a build logs to keeps working. Calling it again
     * is a no-op rather than a second writer duplicating every line, because
     * platforms with more than one entry point (an iOS app with a CarPlay scene)
     * cannot easily promise a single call.
     */
    fun enableRecentLines(capacity: Int = DEFAULT_RECENT_LINES) {
        if (recentWriter != null) return
        val writer = RecentLogWriter(capacity)
        recentWriter = writer
        Logger.addLogWriter(writer)
    }

    /**
     * Keeps the same lines in a file under [directory], so they outlive the
     * process that wrote them.
     *
     * [enableRecentLines] alone answers the question it exists for backwards:
     * it holds a run's log right up until the run is long enough for the OS to
     * reclaim the app, and the long runs are the ones worth reading. A drive
     * that ends with the app killed and relaunched leaves a tail covering the
     * relaunch and nothing else.
     *
     * Once this is on, [recentLines] reads the file rather than memory. The
     * file keeps one rotated generation at [maxBytes] each, so what it holds is
     * bounded without being one run long.
     *
     * Adds a writer, like [enableRecentLines], and can be called with or
     * without it; keeping both means a platform with no filesystem (the
     * browser) still has the in-memory tail. On iOS call it *after*
     * [enableDeviceVisibleLogging], which replaces the configured writers
     * rather than adding to them. Calling it again is a no-op.
     *
     * @param directory an existing directory the app may write to, and that the
     *   OS will not reclaim between the run and reading it back — `filesDir` on
     *   Android, Documents on iOS.
     */
    fun enableRecentLinesFile(
        directory: String,
        fileName: String,
        maxBytes: Long = DEFAULT_RECENT_FILE_BYTES,
    ) {
        if (recentFile != null) return
        val file = openLogFile(directory, fileName, maxBytes) ?: return
        recentFile = file
        Logger.addLogWriter(LogFileWriter(file))
    }

    /**
     * The retained log lines, oldest first — from the file once
     * [enableRecentLinesFile] has been called, otherwise from memory, and empty
     * until one of the two has been.
     */
    fun recentLines(): List<String> = recentFile?.lines() ?: recentWriter?.snapshot() ?: emptyList()

    fun info(tag: String, message: String) {
        Logger.i(messageString = message, tag = tag)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        Logger.w(messageString = message, throwable = throwable, tag = tag)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Logger.e(messageString = message, throwable = throwable, tag = tag)
    }

    private const val DEFAULT_RECENT_LINES = 5_000

    // Two generations of this is ~20k lines at the ~100 bytes a line runs to:
    // comfortably longer than a run worth reading, and small enough to mail.
    private const val DEFAULT_RECENT_FILE_BYTES = 1L * 1024 * 1024
}
