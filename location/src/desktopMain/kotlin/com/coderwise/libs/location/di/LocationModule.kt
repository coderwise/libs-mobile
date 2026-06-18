@file:JvmName("LocationModuleDesktop")
package com.coderwise.libs.location.di

import com.coderwise.libs.location.DesktopLocationProvider
import com.coderwise.libs.location.LocationProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformLocationModule: Module = module {
    single<LocationProvider> { DesktopLocationProvider() }
}
