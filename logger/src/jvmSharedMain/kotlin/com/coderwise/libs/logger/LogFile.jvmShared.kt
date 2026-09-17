package com.coderwise.libs.logger

import java.io.File

internal actual fun openLogFile(
    directory: String,
    fileName: String,
    maxBytes: Long,
): LogFile? {
    val target = File(directory, fileName)
    target.parentFile?.mkdirs()
    // Nothing can be written here, so say so and let the caller keep whatever it
    // holds in memory rather than hand back a file that silently drops lines.
    if (target.parentFile?.isDirectory != true) return null
    return JvmLogFile(target, maxBytes)
}

/**
 * [LogFile] over [java.io.File].
 *
 * Every line is opened, appended and closed. A session is a few thousand lines
 * over an hour, so the per-line cost is irrelevant next to what it buys: a line
 * that returns is on disk even if the OS kills the app the moment after —
 * which is the whole point, since "the process died mid-run" is one of the
 * things such a log exists to prove.
 *
 * Failures are swallowed rather than thrown. This runs inside a
 * [co.touchlab.kermit.LogWriter], so an exception would propagate into whatever
 * call site logged the line, and a diagnostic that crashes the run it is
 * diagnosing is worse than no diagnostic.
 */
private class JvmLogFile(
    private val file: File,
    private val maxBytes: Long,
) : LogFile {

    private val rotated = File(file.parentFile, "${file.name}.1")

    @Synchronized
    override fun append(line: String) {
        runCatching {
            rotateIfOversized()
            file.appendText("$line\n")
        }
    }

    @Synchronized
    override fun lines(): List<String> =
        runCatching { rotated.linesOrEmpty() + file.linesOrEmpty() }.getOrElse { emptyList() }

    private fun rotateIfOversized() {
        if (file.length() < maxBytes) return
        rotated.delete()
        file.renameTo(rotated)
    }
}

private fun File.linesOrEmpty(): List<String> = if (exists()) readLines() else emptyList()
