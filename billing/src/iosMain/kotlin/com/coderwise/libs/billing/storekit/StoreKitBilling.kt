package com.coderwise.libs.billing.storekit

import com.coderwise.libs.billing.Billing
import com.coderwise.libs.billing.BillingProduct
import com.coderwise.libs.billing.PurchaseResult
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

private const val NO_BRIDGE = "no StoreKit bridge was installed by the iOS app"

/**
 * Turns [StoreKitBridge]'s completion handlers into the suspending [Billing]
 * API. Deliberately holds no store state of its own: everything it reports
 * comes from StoreKit through the bridge, so the two halves cannot drift.
 */
internal class StoreKitBilling : Billing {

    private val _entitlements = MutableStateFlow<Set<String>>(emptySet())
    override val entitlements: StateFlow<Set<String>> = _entitlements.asStateFlow()

    override val isAvailable: Boolean get() = installedStoreKitBridge != null

    private var observing = false

    /**
     * "Answered" here means a bridge existed and called back: [StoreKitBridge]
     * reports completion rather than success, and StoreKit's own
     * `currentEntitlements` does not fail so much as come back empty. A build
     * with no bridge installed is the case that must not be mistaken for an
     * honest empty answer.
     */
    override suspend fun refresh(): Boolean {
        val bridge = bridge() ?: return false
        suspendCancellableCoroutine { continuation ->
            bridge.refresh { if (continuation.isActive) continuation.resume(Unit) }
        }
        return true
    }

    override suspend fun products(ids: Set<String>): List<BillingProduct> {
        val bridge = bridge() ?: return emptyList()
        return suspendCancellableCoroutine { continuation ->
            bridge.products(ids) { products -> if (continuation.isActive) continuation.resume(products) }
        }
    }

    override suspend fun purchase(productId: String): PurchaseResult {
        val bridge = bridge() ?: return PurchaseResult.Failed(NO_BRIDGE)
        return suspendCancellableCoroutine { continuation ->
            bridge.purchase(productId) { result -> if (continuation.isActive) continuation.resume(result) }
        }
    }

    override suspend fun restore(): Boolean {
        val bridge = bridge() ?: return false
        suspendCancellableCoroutine { continuation ->
            bridge.restore { if (continuation.isActive) continuation.resume(Unit) }
        }
        return true
    }

    /**
     * Resolved per call rather than once at construction: Koin can build this
     * singleton before the app installs the bridge, and a build that resolved a
     * null once would then stay permanently storeless. Subscribing here, on the
     * first call that finds a bridge, is also what keeps [entitlements] fed
     * without the app having to remember a second start-up step.
     */
    private fun bridge(): StoreKitBridge? = installedStoreKitBridge?.also {
        if (!observing) {
            observing = true
            it.observeEntitlements { ids -> _entitlements.value = ids }
        }
    }
}
