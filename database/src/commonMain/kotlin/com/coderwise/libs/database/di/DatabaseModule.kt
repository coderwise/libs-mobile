package com.coderwise.libs.database.di

import org.koin.core.module.Module
import org.koin.dsl.module

expect val databaseDriverModule: Module

val databaseModule = module {
    includes(databaseDriverModule)
}
