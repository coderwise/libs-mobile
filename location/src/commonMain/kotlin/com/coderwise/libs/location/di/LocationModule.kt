package com.coderwise.libs.location.di

import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformLocationModule: Module

val locationModule = module {
    includes(platformLocationModule)
}
