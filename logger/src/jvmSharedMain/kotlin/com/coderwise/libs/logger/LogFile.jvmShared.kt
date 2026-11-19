package com.coderwise.libs.logger

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

internal actual fun openLogFile(
    directory: String,
    fileName: String,
    maxBytes: Long,
): LogFile? {
    val target = Path(directory, fileName)
    val parent = target.parent ?: return null
    runCatching { SystemFileSystem.createDirectories(parent) }
    // Nothing can be written here, so say so and let the caller keep whatever it
    // holds in memory rather than hand back a file that silently drops lines.
    if (SystemFileSystem.metadataOrNull(parent)?.isDirectory != true) return null
    return JvmLogFile(target, maxBytes)
}

/**
 * [LogFile] over a kotlinx-io [Path].
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
    private val file: Path,
    private val maxBytes: Long,
) : LogFile {

    private val rotated = Path(requireNotNull(file.parent), "${file.name}.1")

    @Synchronized
    override fun append(line: String) {
        runCatching {
            rotateIfOversized()
            SystemFileSystem.sink(file, append = true).buffered().use { it.writeString("$line\n") }
        }
    }

    @Synchronized
    override fun lines(): List<String> =
        runCatching { rotated.linesOrEmpty() + file.linesOrEmpty() }.getOrElse { emptyList() }

    private fun rotateIfOversized() {
        if ((SystemFileSystem.metadataOrNull(file)?.size ?: 0L) < maxBytes) return
        SystemFileSystem.delete(rotated, mustExist = false)
        SystemFileSystem.atomicMove(file, rotated)
    }
}

private fun Path.linesOrEmpty(): List<String> {
    if (!SystemFileSystem.exists(this)) return emptyList()
    val text = SystemFileSystem.source(this).buffered().use { it.readString() }
    return text.lines().let { if (it.lastOrNull().isNullOrEmpty()) it.dropLast(1) else it }
}
