package com.coderwise.libs.navigation.di

import com.coderwise.libs.navigation.NavEventHandler
import com.coderwise.libs.navigation.NavigationState
import com.coderwise.libs.navigation.Navigator
import com.coderwise.libs.navigation.ScreenNavigator
import org.koin.dsl.module

/**
 * One app-wide [NavigationState], also served as [Navigator], [ScreenNavigator] and
 * [NavEventHandler]. ViewModels should ask for the narrowest of these they need.
 */
val navigationModule = module {
    single { NavigationState() }
    factory<Navigator> { get<NavigationState>() }
    factory<ScreenNavigator> { get<NavigationState>() }
    factory<NavEventHandler> { get<NavigationState>() }
}
