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
    fun info(tag: String, message: String) {
        Logger.i(messageString = message, tag = tag)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        Logger.w(messageString = message, throwable = throwable, tag = tag)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Logger.e(messageString = message, throwable = throwable, tag = tag)
    }
}
