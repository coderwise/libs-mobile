@file:JvmName("SettingsModuleDesktop")
package com.coderwise.libs.settings.di

import com.coderwise.libs.settings.SettingsDataStoreFactory
import org.koin.dsl.module
import org.koin.core.module.Module

actual val platformSettingsModule: Module = module {
    single { SettingsDataStoreFactory() }
}
