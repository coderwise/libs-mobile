package com.coderwise.libs.location.di

import com.coderwise.libs.location.LocationProvider
import com.coderwise.libs.location.WasmJsLocationProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformLocationModule: Module = module {
    single<LocationProvider> { WasmJsLocationProvider() }
}
