@file:OptIn(ExperimentalForeignApi::class)

package com.coderwise.libs.shell

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.posix.fread
import platform.posix.getenv
import platform.posix.pclose
import platform.posix.popen

/**
 * Kotlin/Native does not expose `posix_spawn`, so the command goes through `popen` and a small `sh` wrapper that
 * starts in [dir], closes stdin (CLIs such as claude wait for piped input otherwise), merges stderr into the output like the JVM
 * does and kills the command after [timeoutSeconds]. Exit status 137 (SIGKILL) is reported as a timeout.
 */
internal actual fun runProcess(dir: String?, command: List<String>, timeoutSeconds: Long): ShellResult? {
    val script = """cd "${'$'}0" || exit 127; "${'$'}@" 2>&1 </dev/null & p=${'$'}!; """ +
        """( sleep $timeoutSeconds; kill -9 ${'$'}p ) >/dev/null 2>&1 & w=${'$'}!; wait ${'$'}p; r=${'$'}?; kill ${'$'}w 2>/dev/null; exit ${'$'}r"""
    val line = (listOf("/bin/sh", "-c", script, dir ?: ".") + command).joinToString(" ") { quote(it) }
    val pipe = popen(line, "r") ?: return null
    val out = StringBuilder()
    val bytes = ByteArray(4096)
    while (true) {
        val n = bytes.usePinned { fread(it.addressOf(0), 1.convert(), bytes.size.convert(), pipe) }.toInt()
        if (n <= 0) break
        out.append(bytes.decodeToString(0, n))
    }
    val status = pclose(pipe)
    val code = if (status and 0x7f == 0) (status shr 8) and 0xff else 128 + (status and 0x7f)
    return if (code == 137) null else ShellResult(code, out.toString())
}

private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

internal actual suspend fun <T> runBlockingIo(block: () -> T): T = withContext(ioDispatcher) { block() }

internal actual fun homeDirectory(): String = getenv("HOME")?.toKString().orEmpty()

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
