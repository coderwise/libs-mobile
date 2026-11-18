@file:JvmName("SettingsModuleDesktop")
package com.coderwise.libs.settings.di

import com.coderwise.libs.settings.SettingsDataStoreFactory
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import kotlinx.io.files.Path

/**
 * The directory desktop settings files go in, which the application has to declare, as a [File] or a
 * kotlinx-io [Path]:
 *
 * ```
 * single<File>(SETTINGS_DIR_QUALIFIER) { File(System.getProperty("user.home"), ".myApp") }
 * ```
 *
 * A library has no business naming a folder after one of the apps that use it, and every one of
 * them keeps its files somewhere of its own.
 */
val SETTINGS_DIR_QUALIFIER = named("com.coderwise.libs.settings.dir")

actual val platformSettingsModule: Module = module {
    single {
        val path = getOrNull<Path>(SETTINGS_DIR_QUALIFIER)
        if (path != null) SettingsDataStoreFactory(path) else SettingsDataStoreFactory(get<File>(SETTINGS_DIR_QUALIFIER))
    }
}
