package com.coderwise.libs.shell

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

internal object DefaultShell : Shell {
    override suspend fun run(dir: String?, command: List<String>, timeoutSeconds: Long): ShellResult? =
        runBlockingIo { runProcess(dir, command, timeoutSeconds) }

    override suspend fun readText(path: String): String? = withContext(ioDispatcher) {
        val file = Path(path)
        if (SystemFileSystem.metadataOrNull(file)?.isRegularFile != true) null
        else SystemFileSystem.source(file).buffered().use { it.readString() }
    }

    override suspend fun writeText(path: String, text: String) = withContext(ioDispatcher) {
        val file = Path(path)
        file.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(file).buffered().use { it.writeString(text) }
    }

    override suspend fun exists(path: String): Boolean = withContext(ioDispatcher) { SystemFileSystem.exists(Path(path)) }

    override suspend fun listDirs(path: String): List<String> = withContext(ioDispatcher) {
        val dir = Path(path)
        if (SystemFileSystem.metadataOrNull(dir)?.isDirectory != true) emptyList()
        else SystemFileSystem.list(dir).filter { SystemFileSystem.metadataOrNull(it)?.isDirectory == true }.map { it.name }
    }

    override suspend fun listEntries(path: String): List<ShellEntry> = withContext(ioDispatcher) {
        val dir = Path(path)
        if (SystemFileSystem.metadataOrNull(dir)?.isDirectory != true) emptyList()
        else SystemFileSystem.list(dir).map { ShellEntry(it.name, SystemFileSystem.metadataOrNull(it)?.isDirectory == true) }
    }

    override suspend fun canonicalPath(path: String): String? = withContext(ioDispatcher) {
        runCatching { SystemFileSystem.resolve(Path(path)).toString() }.getOrNull()
    }

    override fun homeDir(): String = homeDirectory()
}

/** Runs [command]; null if it could not start or timed out, or the target cannot spawn processes. */
internal expect fun runProcess(dir: String?, command: List<String>, timeoutSeconds: Long): ShellResult?

/**
 * Runs blocking [block] off the caller's thread. Where the platform can, cancelling the caller interrupts [block],
 * which [runProcess] answers by killing the process.
 */
internal expect suspend fun <T> runBlockingIo(block: () -> T): T

internal expect fun homeDirectory(): String

/** `Dispatchers.IO` is not available in common code. */
internal expect val ioDispatcher: CoroutineDispatcher
