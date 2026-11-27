package com.coderwise.libs.shell

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The output is read on a second thread so this one can wait with a timeout and notice an interrupt (a cancelled
 * caller). Interrupted or timed out, the process and everything it started are killed.
 */
internal actual fun runProcess(dir: String?, command: List<String>, timeoutSeconds: Long): ShellResult? {
    val p = try {
        ProcessBuilder(command).apply { dir?.let { directory(File(it)) } }.redirectErrorStream(true).start()
    } catch (e: IOException) {
        return null
    }
    p.outputStream.close() // no stdin: CLIs such as claude wait for piped input otherwise
    val out = StringBuffer()
    val reader = thread(isDaemon = true) { runCatching { out.append(p.inputStream.bufferedReader().readText()) } }
    try {
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            kill(p)
            return null
        }
        reader.join(READER_GRACE_MS) // a surviving grandchild could keep the pipe open
        return ShellResult(p.exitValue(), out.toString())
    } catch (e: InterruptedException) {
        kill(p)
        throw e
    }
}

private fun kill(p: Process) {
    // Process.descendants() is Java 9+ and not in Android's API 26, so call it reflectively where it exists.
    runCatching {
        (Process::class.java.getMethod("descendants").invoke(p) as java.util.stream.Stream<*>).forEach {
            Class.forName("java.lang.ProcessHandle").getMethod("destroyForcibly").invoke(it)
        }
    }
    p.destroyForcibly()
}

private const val READER_GRACE_MS = 5_000L

internal actual suspend fun <T> runBlockingIo(block: () -> T): T = withContext(Dispatchers.IO) { runInterruptible { block() } }

internal actual fun homeDirectory(): String = System.getProperty("user.home").orEmpty()

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
