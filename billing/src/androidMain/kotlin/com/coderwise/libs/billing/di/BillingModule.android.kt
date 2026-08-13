package com.coderwise.libs.billing.di

import android.app.Application
import android.content.Context
import com.coderwise.libs.billing.Billing
import com.coderwise.libs.billing.play.PlayBilling
import org.koin.core.scope.Scope

/**
 * The `Context` comes from `androidContext()` in each Android app's Koin start-up.
 * It is narrowed to the Application here so the store client and the foreground
 * tracker are both anchored to the process rather than to whatever happened to
 * resolve them first.
 */
internal actual fun createBilling(scope: Scope): Billing =
    PlayBilling(scope.get<Context>().applicationContext as Application)
