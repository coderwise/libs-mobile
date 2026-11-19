package com.coderwise.libs.settings.di

import com.coderwise.libs.settings.SettingsDataStoreFactory
import kotlinx.io.files.Path
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The directory macOS settings files go in, which the application has to declare:
 *
 * ```
 * single<Path>(SETTINGS_DIR_QUALIFIER) { Path(home, "Library/Application Support/MyApp") }
 * ```
 */
val SETTINGS_DIR_QUALIFIER = named("com.coderwise.libs.settings.dir")

actual val platformSettingsModule: Module = module {
    single { SettingsDataStoreFactory(get<Path>(SETTINGS_DIR_QUALIFIER)) }
}
