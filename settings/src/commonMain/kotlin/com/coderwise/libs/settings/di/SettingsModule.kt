package com.coderwise.libs.settings.di

import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformSettingsModule: Module

val settingsModule = module {
    includes(platformSettingsModule)
}
