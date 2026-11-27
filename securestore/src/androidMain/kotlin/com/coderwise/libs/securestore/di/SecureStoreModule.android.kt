package com.coderwise.libs.securestore.di

import com.coderwise.libs.securestore.KeystoreSecureStore
import com.coderwise.libs.securestore.SecureStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformSecureStoreModule: Module = module {
    single<SecureStore> { KeystoreSecureStore(androidContext()) }
}
