package com.coderwise.libs.billing.play

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.coderwise.libs.billing.PurchaseResult

/**
 * Reads one purchase-flow callback as an outcome.
 *
 * Play answers a purchase attempt in two places at once — a response code and a
 * list that may be null, empty, or full of purchases in either state — and the
 * combinations do not line up with what a caller wants to know.
 *
 * Takes the response code and the purchase states rather than Play's own types
 * so the reading can be exercised on a plain JVM: `Purchase` is only
 * constructible from receipt JSON, which drags in the Android stubs that host
 * tests do not have.
 */
internal fun purchaseOutcome(
    responseCode: Int,
    debugMessage: String,
    purchaseStates: List<Int>,
): PurchaseResult = when {
    responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled

    // Already owned is a success from the buyer's side: they have the unlock, and
    // the only thing wrong was our idea of what they own. Reported as bought so
    // the caller re-reads entitlements instead of showing an error over a
    // product the person already paid for.
    responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseResult.Purchased

    responseCode != BillingClient.BillingResponseCode.OK -> PurchaseResult.Failed(debugMessage)

    purchaseStates.any { it == Purchase.PurchaseState.PURCHASED } -> PurchaseResult.Purchased

    purchaseStates.any { it == Purchase.PurchaseState.PENDING } -> PurchaseResult.Pending

    else -> PurchaseResult.Failed("Play reported success with nothing purchased")
}

/**
 * Product ids the buyer actually owns right now.
 *
 * A pending purchase is deliberately not one of them — the money has not moved
 * yet, and Play can still fail it days later.
 */
internal fun ownedProductIds(purchases: List<Purchase>): Set<String> =
    purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        .flatMap { it.products }
        .toSet()
