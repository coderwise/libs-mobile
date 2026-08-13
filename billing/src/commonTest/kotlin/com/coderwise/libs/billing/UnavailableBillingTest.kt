package com.coderwise.libs.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class UnavailableBillingTest {

    @Test
    fun `reports no store`() {
        assertFalse(UnavailableBilling.isAvailable)
    }

    @Test
    fun `owns nothing`() = runTest {
        UnavailableBilling.refresh()
        UnavailableBilling.restore()

        assertTrue(UnavailableBilling.entitlements.value.isEmpty())
    }

    @Test
    fun `sells nothing`() = runTest {
        assertEquals(emptyList(), UnavailableBilling.products(setOf("premium_lifetime")))
    }

    @Test
    fun `fails a purchase instead of pretending it worked`() = runTest {
        assertIs<PurchaseResult.Failed>(UnavailableBilling.purchase("premium_lifetime"))
    }
}
