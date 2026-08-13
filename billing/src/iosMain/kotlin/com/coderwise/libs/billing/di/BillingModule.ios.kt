package com.coderwise.libs.billing.di

import com.coderwise.libs.billing.Billing
import com.coderwise.libs.billing.storekit.StoreKitBilling
import org.koin.core.scope.Scope

/**
 * Built even when no bridge has been installed yet — it resolves one per call,
 * so DI start-up and the iOS app's StoreKit set-up need no ordering between them.
 */
internal actual fun createBilling(scope: Scope): Billing = StoreKitBilling()
