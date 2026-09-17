package com.coderwise.libs.logger

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

@OptIn(ExperimentalForeignApi::class)
internal actual fun openLogFile(
    directory: String,
    fileName: String,
    maxBytes: Long,
): LogFile? {
    if (!NSFileManager.defaultManager.fileExistsAtPath(directory)) return null
    return NsLogFile("$directory/$fileName", maxBytes)
}

/**
 * The app's Documents directory, or null if it can't be resolved.
 *
 * Documents deliberately, not Caches: the point of a log file is that iOS must
 * not reclaim it between the run and reading it back. It also pulls off a
 * device the same way a database or settings file does:
 *
 *     xcrun devicectl device copy from --device <id> \
 *       --domain-type appDataContainer --domain-identifier <bundle-id> \
 *       --source Documents/<fileName> --destination ./<fileName>
 */
@OptIn(ExperimentalForeignApi::class)
internal fun documentsDirectory(): String? {
    val documents: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return documents?.path
}

/**
 * [LogFile] over POSIX file calls.
 *
 * Every line is opened, appended and closed, so a line that returns is on disk
 * even if iOS kills the app a moment later. Volume is low enough (a session is
 * a few thousand lines) that the per-line cost of the open/close is irrelevant.
 *
 * A failure is silent: this runs inside a [co.touchlab.kermit.LogWriter], and a
 * diagnostic that throws into the run it is diagnosing is worse than no
 * diagnostic.
 */
@OptIn(ExperimentalForeignApi::class)
private class NsLogFile(
    private val path: String,
    private val maxBytes: Long,
) : LogFile {

    private val rotatedPath = "$path.1"

    override fun append(line: String) {
        rotateIfOversized()
        val file = fopen(path, "a") ?: return
        fputs("$line\n", file)
        fclose(file)
    }

    override fun lines(): List<String> = readLines(rotatedPath) + readLines(path)

    private fun readLines(from: String): List<String> {
        val text = NSString.stringWithContentsOfFile(from, NSUTF8StringEncoding, null) ?: return emptyList()
        return text.lineSequence().filter { it.isNotEmpty() }.toList()
    }

    private fun rotateIfOversized() {
        val manager = NSFileManager.defaultManager
        val size = manager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? Long ?: return
        if (size < maxBytes) return
        manager.removeItemAtPath(rotatedPath, null)
        manager.moveItemAtPath(path, rotatedPath, null)
    }
}
