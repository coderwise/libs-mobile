package com.coderwise.libs.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Null object for platforms with no store behind them — desktop and web, which
 * are developer surfaces rather than distribution channels. Owns nothing, sells
 * nothing, and keeps the DI graph the same shape everywhere.
 */
object UnavailableBilling : Billing {
    override val entitlements: StateFlow<Set<String>> = MutableStateFlow(emptySet<String>()).asStateFlow()

    override val isAvailable: Boolean = false

    /** Never answered rather than answered-empty: there is no store to ask. */
    override suspend fun refresh(): Boolean = false

    override suspend fun products(ids: Set<String>): List<BillingProduct> = emptyList()

    override suspend fun purchase(productId: String): PurchaseResult =
        PurchaseResult.Failed("this build was not installed from a store")

    override suspend fun restore(): Boolean = false
}
