package com.coderwise.libs.billing.play

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase.PurchaseState
import com.coderwise.libs.billing.PurchaseResult
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayPurchaseMappingTest {

    @Test
    fun `a purchased item is bought`() {
        assertEquals(
            PurchaseResult.Purchased,
            purchaseOutcome(BillingResponseCode.OK, "", listOf(PurchaseState.PURCHASED)),
        )
    }

    @Test
    fun `backing out of the sheet is not an error`() {
        assertEquals(
            PurchaseResult.Cancelled,
            purchaseOutcome(BillingResponseCode.USER_CANCELED, "", emptyList()),
        )
    }

    @Test
    fun `an item already owned counts as bought`() {
        assertEquals(
            PurchaseResult.Purchased,
            purchaseOutcome(BillingResponseCode.ITEM_ALREADY_OWNED, "", emptyList()),
        )
    }

    @Test
    fun `an out-of-band payment is pending, not owned`() {
        assertEquals(
            PurchaseResult.Pending,
            purchaseOutcome(BillingResponseCode.OK, "", listOf(PurchaseState.PENDING)),
        )
    }

    @Test
    fun `one purchased item among pending ones is enough`() {
        assertEquals(
            PurchaseResult.Purchased,
            purchaseOutcome(BillingResponseCode.OK, "", listOf(PurchaseState.PENDING, PurchaseState.PURCHASED)),
        )
    }

    @Test
    fun `a store error carries its debug message`() {
        assertEquals(
            PurchaseResult.Failed("no network"),
            purchaseOutcome(BillingResponseCode.NETWORK_ERROR, "no network", emptyList()),
        )
    }

    @Test
    fun `success with an empty list is a failure, not a purchase`() {
        assertEquals(
            PurchaseResult.Failed("Play reported success with nothing purchased"),
            purchaseOutcome(BillingResponseCode.OK, "", emptyList()),
        )
    }
}
