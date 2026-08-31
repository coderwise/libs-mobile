package com.coderwise.libs.billing.di

import com.coderwise.libs.billing.Billing
import com.coderwise.libs.billing.UnavailableBilling
import org.koin.core.scope.Scope

/** The web build is a development surface; nothing is sold through it. */
internal actual fun createBilling(scope: Scope): Billing = UnavailableBilling
