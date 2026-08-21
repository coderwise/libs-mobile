package com.coderwise.libs.billing.play

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.coderwise.libs.billing.Billing
import com.coderwise.libs.billing.BillingProduct
import com.coderwise.libs.billing.PurchaseResult
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Google Play Billing behind the shared [Billing] API.
 *
 * Everything here is one-time products (`INAPP`) and never a subscription,
 * which is what lets ownership be settled by `queryPurchasesAsync` on the
 * device with no validation server behind it.
 */
internal class PlayBilling(application: Application) : Billing {

    private val _entitlements = MutableStateFlow<Set<String>>(emptySet())
    override val entitlements: StateFlow<Set<String>> = _entitlements.asStateFlow()

    override val isAvailable: Boolean = true

    private val foreground = ForegroundActivityTracker(application)

    /** Serialises connection attempts so a burst of calls opens one connection. */
    private val connecting = Mutex()

    /**
     * Live only while a purchase sheet is up. Play answers the sheet through the
     * client-wide listener rather than the call that opened it, so the caller's
     * coroutine has to be parked somewhere reachable from that callback.
     */
    @Volatile
    private var pending: CancellableContinuation<PurchaseResult>? = null

    private val client = BillingClient.newBuilder(application)
        // Play unbinds the service whenever its own process is recycled. Left to
        // us, that would surface as a purchase failing for no reason the buyer
        // could act on, so let the client reconnect itself.
        .enableAutoServiceReconnection()
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener(::onPurchasesUpdated)
        .build()

    override suspend fun refresh(): Boolean {
        if (!connected()) return false
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val (result, purchases) = suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(params) { result, list ->
                if (continuation.isActive) continuation.resume(result to list)
            }
        }
        // A failed query still hands back an empty list, which is
        // indistinguishable from owning nothing. Writing that down would report
        // a network blip as a refund, so it stops here instead.
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return false
        purchases.forEach(::acknowledge)
        // Assigned, not merged: a refund has to be able to take the unlock back.
        _entitlements.value = ownedProductIds(purchases)
        return true
    }

    override suspend fun products(ids: Set<String>): List<BillingProduct> =
        details(ids).mapNotNull { detail ->
            detail.oneTimePurchaseOfferDetailsList?.firstOrNull()?.let { offer ->
                BillingProduct(
                    id = detail.productId,
                    title = detail.title,
                    formattedPrice = offer.formattedPrice,
                )
            }
        }

    override suspend fun purchase(productId: String): PurchaseResult {
        val activity = foreground.current
        val outcome = when {
            !connected() -> PurchaseResult.Failed("Play Billing is unavailable on this device")
            activity == null -> PurchaseResult.Failed("no foreground activity to host the purchase sheet")
            else -> details(setOf(productId)).firstOrNull()
                ?.let { launchFlow(activity, it) }
                ?: PurchaseResult.Failed("Play has no product named $productId")
        }
        // Re-read rather than trust the callback's payload: an ITEM_ALREADY_OWNED
        // answer carries no purchase list at all, and that is precisely the case
        // where the local entitlement set is the thing that was wrong.
        if (outcome == PurchaseResult.Purchased) refresh()
        return outcome
    }

    /** Play keeps no separate restore path — ownership comes from the same query. */
    override suspend fun restore(): Boolean = refresh()

    private suspend fun connected(): Boolean = connecting.withLock {
        if (client.isReady) {
            true
        } else {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        // Guarded because auto-reconnection calls this again on
                        // every later reconnect, long after we resumed.
                        if (continuation.isActive) {
                            continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) continuation.resume(false)
                    }
                })
            }
        }
    }

    private suspend fun details(ids: Set<String>): List<ProductDetails> {
        if (ids.isEmpty() || !connected()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()
        return suspendCancellableCoroutine { continuation ->
            client.queryProductDetailsAsync(params) { _, result ->
                if (continuation.isActive) continuation.resume(result.productDetailsList)
            }
        }
    }

    private suspend fun launchFlow(activity: Activity, details: ProductDetails): PurchaseResult =
        suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            val launch = client.launchBillingFlow(activity, params)
            if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
                // No sheet was shown, so the purchases listener will never fire.
                pending = null
                if (continuation.isActive) continuation.resume(PurchaseResult.Failed(launch.debugMessage))
            }
        }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val updated = purchases.orEmpty()
        updated.forEach(::acknowledge)
        if (updated.isNotEmpty()) _entitlements.update { it + ownedProductIds(updated) }
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) {
            continuation.resume(
                purchaseOutcome(result.responseCode, result.debugMessage.orEmpty(), updated.map { it.purchaseState })
            )
        }
    }

    /**
     * Play automatically refunds a purchase left unacknowledged for three days,
     * so this is the one store call that is not optional. It is fired again from
     * every [refresh] because a failure here is invisible until then — the
     * purchase simply comes back with `isAcknowledged` still false.
     */
    private fun acknowledge(purchase: Purchase) {
        val unacknowledged = purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged
        if (!unacknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { }
    }
}
