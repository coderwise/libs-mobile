package com.coderwise.libs.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

/**
 * Appends log lines to a file in the app's Documents directory.
 *
 * [CommonWriter][co.touchlab.kermit.CommonWriter] only reaches stdout, which
 * exists solely while the phone is tethered to a Mac running
 * `devicectl ... --console`. That is exactly the situation a field test is not:
 * the interesting run happens with the phone in a pocket, and afterwards there
 * is nothing to inspect but the app's own state and a guess. A file survives the
 * run, and pulls off the device the same way a database or settings file does:
 *
 *     xcrun devicectl device copy from --device <id> \
 *       --domain-type appDataContainer --domain-identifier <bundle-id> \
 *       --source Documents/<fileName> --destination ./<fileName>
 *
 * Documents deliberately, not Caches: the point is that iOS must not reclaim it
 * between the run and reading it.
 *
 * Every line is opened, appended and closed, so a line that returns is on disk
 * even if iOS kills the app a moment later — which matters, since "the app was
 * suspended mid-run" is one of the things the log exists to prove. Volume is
 * low enough (a session is a few hundred lines) that the per-line cost of the
 * open/close is irrelevant.
 *
 * At [maxFileBytes] the file rotates to `<path>.1`, replacing any previous
 * rotation, so there is always at least one full file of recent history and
 * never more than twice the cap sitting in the container.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)
internal class FileLogWriter(
    private val path: String,
    private val maxFileBytes: Long,
) : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val line = buildString {
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
            append('\n')
        }
        append(line)
    }

    private fun append(line: String) {
        rotateIfOversized(NSFileManager.defaultManager)
        val file = fopen(path, "a") ?: return
        fputs(line, file)
        fclose(file)
    }

    private fun rotateIfOversized(manager: NSFileManager) {
        val size = manager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? Long ?: return
        if (size < maxFileBytes) return
        val rotated = "$path.1"
        manager.removeItemAtPath(rotated, null)
        manager.moveItemAtPath(path, rotated, null)
    }
}

/** `Documents/[fileName]`, or null if the Documents directory can't be resolved. */
@OptIn(ExperimentalForeignApi::class)
internal fun logFilePath(fileName: String): String? {
    val documents: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val base = documents?.path ?: return null
    return "$base/$fileName"
}
