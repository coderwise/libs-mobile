package com.coderwise.libs.shell

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** iOS and web cannot spawn processes. */
internal actual fun runProcess(dir: String?, command: List<String>, timeoutSeconds: Long): ShellResult? = null

internal actual suspend fun <T> runBlockingIo(block: () -> T): T = withContext(ioDispatcher) { block() }

internal actual fun homeDirectory(): String = ""

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
