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
     * The retained log lines, oldest first — empty until
     * [enableRecentLines] has been called.
     */
    fun recentLines(): List<String> = recentWriter?.snapshot() ?: emptyList()

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
}
