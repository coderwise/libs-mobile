package com.coderwise.libs.billing.di

import com.coderwise.libs.billing.Billing
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * One [Billing] per process: each implementation holds a live connection to the
 * store and, on Android, the purchase-flow listener that a second instance
 * would silently steal.
 */
val billingModule = module {
    single<Billing> { createBilling(this) }
}

/**
 * Takes the Koin [Scope] rather than nothing at all because Android's store
 * client needs the `Context` that `androidContext()` put there — the one
 * dependency that cannot be expressed in common code.
 */
internal expect fun createBilling(scope: Scope): Billing
