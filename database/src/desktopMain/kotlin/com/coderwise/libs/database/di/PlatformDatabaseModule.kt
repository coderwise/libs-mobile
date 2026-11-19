package com.coderwise.libs.database.di

import com.coderwise.libs.database.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlinx.io.files.Path

/**
 * The directory the desktop database file goes in, which the application has to declare:
 *
 * ```
 * single<Path>(DATA_DIR_QUALIFIER) { Path(System.getProperty("user.home"), ".myApp") }
 * ```
 *
 * A library has no business naming a folder after one of the apps that use it, and every one of
 * them keeps its files somewhere of its own.
 */
val DATA_DIR_QUALIFIER = named("com.coderwise.libs.database.dataDir")

actual val databaseDriverModule: Module = module {
    single { DatabaseDriverFactory(get<Path>(DATA_DIR_QUALIFIER)) }
}
