package com.coderwise.libs.database.di

import com.coderwise.libs.database.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseDriverModule: Module = module {
    single { DatabaseDriverFactory() }
}
