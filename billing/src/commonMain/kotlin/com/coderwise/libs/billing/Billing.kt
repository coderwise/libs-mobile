package com.coderwise.libs.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * Something the platform store can sell.
 *
 * Only non-consumables are modelled — a lifetime unlock and the like. That
 * shape is what keeps this library backend-free: ownership of a non-consumable
 * is answerable on-device, while subscriptions (renewals, expiry, grace
 * periods, billing retries) are not.
 */
data class BillingProduct(
    val id: String,
    val title: String,
    /**
     * Already formatted by the store in the buyer's own currency. Never assemble
     * a price locally — the store is the only place that knows the local tax
     * treatment and the price tier the buyer actually sees.
     */
    val formattedPrice: String,
)

/** What came back from a single [Billing.purchase] attempt. */
sealed interface PurchaseResult {
    /** Paid and owned; [Billing.entitlements] already carries the product id. */
    data object Purchased : PurchaseResult

    /** The buyer backed out of the store's sheet. Not an error — say nothing. */
    data object Cancelled : PurchaseResult

    /**
     * Payment is being settled out of band (cash top-up on Play, Ask-to-Buy on
     * iOS). Nothing is owned yet; the entitlement shows up in
     * [Billing.entitlements] if and when it clears, possibly days later.
     */
    data object Pending : PurchaseResult

    /** The store refused. [message] is for logs, not for the buyer. */
    data class Failed(val message: String) : PurchaseResult
}

/**
 * Non-consumable purchases against whichever store this build was installed
 * from — Play Billing on Android, StoreKit 2 on iOS, nothing anywhere else.
 *
 * Ownership is read from the store on every launch rather than cached as truth
 * locally: it is the store that knows about refunds, family sharing, and a
 * reinstall on a new device.
 */
interface Billing {
    /**
     * Product ids this install currently owns. Starts empty — nothing has been
     * asked of the store yet — so treat "not in the set" as "unknown or not
     * owned" until the first [refresh] completes, and never gate a destructive
     * or irreversible decision on its emptiness.
     */
    val entitlements: StateFlow<Set<String>>

    /**
     * False where there is no store to talk to at all (desktop, web). Everything
     * else here stays callable and inert, so callers need no platform branches.
     */
    val isAvailable: Boolean

    /**
     * Silently re-reads ownership from the store into [entitlements]. Safe at
     * startup: it never shows UI and never asks the buyer to authenticate.
     */
    suspend fun refresh()

    /** Store-formatted details for [ids]; ids the store doesn't know are dropped. */
    suspend fun products(ids: Set<String>): List<BillingProduct>

    /** Shows the store's purchase sheet and suspends until the buyer is done with it. */
    suspend fun purchase(productId: String): PurchaseResult

    /**
     * The "Restore purchases" button, and only that. Unlike [refresh] this may
     * prompt for the store account password, so it must stay user-initiated —
     * Apple requires the affordance to exist for non-consumables.
     */
    suspend fun restore()
}
