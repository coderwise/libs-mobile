package com.coderwise.libs.securestore.di

import com.coderwise.libs.securestore.KeychainSecureStore
import com.coderwise.libs.securestore.SecureStore
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

actual val platformSecureStoreModule: Module = module {
    // The keychain service is the app's bundle id, so every app keeps its own items.
    single<SecureStore> { KeychainSecureStore(NSBundle.mainBundle.bundleIdentifier ?: "securestore") }
}
