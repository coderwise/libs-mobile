package com.coderwise.libs.securestore.di

import com.coderwise.libs.securestore.MemorySecureStore
import com.coderwise.libs.securestore.SecureStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformSecureStoreModule: Module = module {
    single<SecureStore> { MemorySecureStore() }
}
