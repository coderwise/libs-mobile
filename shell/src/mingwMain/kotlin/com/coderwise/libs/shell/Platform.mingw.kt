@file:OptIn(ExperimentalForeignApi::class)

package com.coderwise.libs.shell

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix._pclose
import platform.posix._popen
import platform.posix.fread
import platform.posix.getenv

/** Through `cmd /c` and `_popen`: stderr is merged, but there is no timeout or cancellation on Windows. */
internal actual fun runProcess(dir: String?, command: List<String>, timeoutSeconds: Long): ShellResult? {
    val cd = dir?.let { "cd /d ${quote(it)} && " }.orEmpty()
    val pipe = _popen("$cd${command.joinToString(" ") { quote(it) }} 2>&1 <nul", "r") ?: return null
    val out = StringBuilder()
    val bytes = ByteArray(4096)
    while (true) {
        val n = bytes.usePinned { fread(it.addressOf(0), 1.convert(), bytes.size.convert(), pipe) }.toInt()
        if (n <= 0) break
        out.append(bytes.decodeToString(0, n))
    }
    return ShellResult(_pclose(pipe), out.toString())
}

private fun quote(s: String) = "\"" + s.replace("\"", "\\\"") + "\""

internal actual suspend fun <T> runBlockingIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }

internal actual fun homeDirectory(): String = getenv("USERPROFILE")?.toKString().orEmpty()

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
