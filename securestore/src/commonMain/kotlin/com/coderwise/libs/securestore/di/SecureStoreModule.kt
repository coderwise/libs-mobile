package com.coderwise.libs.securestore.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** Platform binding of [com.coderwise.libs.securestore.SecureStore]. */
expect val platformSecureStoreModule: Module

/** Include this in the app's Koin modules to get a `SecureStore`. */
val secureStoreModule = module {
    includes(platformSecureStoreModule)
}
