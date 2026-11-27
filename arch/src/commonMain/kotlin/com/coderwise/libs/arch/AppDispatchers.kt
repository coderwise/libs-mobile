package com.coderwise.libs.arch

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatchers that common code cannot name directly. */
object AppDispatchers {
    /**
     * For blocking disk and database work: `Dispatchers.IO` on Android, desktop and every native target, and
     * `Dispatchers.Default` on the web, which has no blocking I/O and no `Dispatchers.IO`.
     */
    val io: CoroutineDispatcher
        get() = platformIoDispatcher
}

internal expect val platformIoDispatcher: CoroutineDispatcher
