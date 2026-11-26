package com.coderwise.libs.desktop

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.context.startKoin

internal expect fun userHomeDir(): String

/** Resolves and creates the app's data directory, starts Koin with the app's modules, and returns the directory. */
internal fun startDesktopKoin(config: DesktopAppConfig): Path {
    val dataDir = config.dataDir ?: Path(userHomeDir(), config.dataDirName)
    SystemFileSystem.createDirectories(dataDir)
    startKoin { modules(config.koinModules(dataDir)) }
    return dataDir
}
