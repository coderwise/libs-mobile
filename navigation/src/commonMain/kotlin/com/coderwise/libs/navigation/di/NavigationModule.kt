package com.coderwise.libs.navigation.di

import com.coderwise.libs.navigation.NavEventSink
import com.coderwise.libs.navigation.NavigationState
import com.coderwise.libs.navigation.Navigator
import com.coderwise.libs.navigation.ScreenNavigator
import org.koin.dsl.module

/**
 * One app-wide [NavigationState], also served as [Navigator], [ScreenNavigator] and
 * [NavEventSink]. ViewModels should ask for the narrowest of these they need.
 */
val navigationModule = module {
    single { NavigationState() }
    factory<Navigator> { get<NavigationState>() }
    factory<ScreenNavigator> { get<NavigationState>() }
    factory<NavEventSink> { get<NavigationState>() }
}
