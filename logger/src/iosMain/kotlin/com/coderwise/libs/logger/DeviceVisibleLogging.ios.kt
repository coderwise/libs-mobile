package com.coderwise.libs.logger

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

private const val TAG = "DeviceVisibleLogging"

/** Default size at which the log file rotates: 4 MB, so two files stay under ~8 MB. */
const val DEFAULT_MAX_LOG_BYTES: Long = 4L * 1024 * 1024

/**
 * Makes debug binaries loggable off-device, two ways.
 *
 * Kermit's os_log/NSLog default marks dynamic message content as private, so
 * anything streamed off-device without a debugger attached (idevicesyslog,
 * Console.app) shows only `<private>` — useless for USB field testing. So debug
 * builds get [CommonWriter], which prints to stdout and is captured by
 * `xcrun devicectl device process launch --console`.
 *
 * stdout only exists while tethered, though, and the runs worth investigating
 * are the untethered ones — a drive with the phone in a pocket, a background
 * session with the screen off. A file covers those: the same lines are kept in
 * `Documents/[fileName]`, to be pulled off the device afterwards.
 *
 * Release binaries keep the os_log default and write no file. Redaction there is
 * a feature, and a plaintext log sitting in the container is not something to
 * ship to users.
 *
 * @param fileName name of the log file inside the app's Documents directory,
 *   e.g. `"myapp.log"`. Per-app, since the container is per-app.
 * @param maxFileBytes size at which the file rotates, keeping one previous
 *   generation as `<fileName>.1`. Defaults to [DEFAULT_MAX_LOG_BYTES].
 */
@OptIn(ExperimentalNativeApi::class)
fun enableDeviceVisibleLogging(
    fileName: String,
    maxFileBytes: Long = DEFAULT_MAX_LOG_BYTES,
) {
    if (!Platform.isDebugBinary) return

    val path = logFilePath(fileName)
    if (path == null) {
        Logger.setLogWriters(CommonWriter())
        AppLogger.warn(TAG, "no Documents directory — file logging disabled, stdout only")
        return
    }
    Logger.setLogWriters(CommonWriter(), FileLogWriter(path, maxFileBytes))
    AppLogger.info(TAG, "logging to stdout and $path")
}
