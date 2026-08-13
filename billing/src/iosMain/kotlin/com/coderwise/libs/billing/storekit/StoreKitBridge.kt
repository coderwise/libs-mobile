package com.coderwise.libs.billing.storekit

import com.coderwise.libs.billing.BillingProduct
import com.coderwise.libs.billing.PurchaseResult
import kotlin.concurrent.Volatile

/**
 * The Swift half of the iOS store.
 *
 * StoreKit 2 is Swift-only — `Product` and `Transaction` are Swift types built
 * on Swift concurrency, and Kotlin/Native's Objective-C interop cannot see
 * either — so the iOS implementation owns no store logic at all. It adapts
 * whatever the iOS app installs through [installStoreKitBridge]. StoreKit 1
 * would be reachable from Kotlin, but its receipt handling is exactly the part that
 * wants a validation server, and staying backend-free is the point.
 *
 * Completion handlers rather than suspend functions: Swift can conform to a
 * Kotlin protocol, but not to one with suspending members.
 */
interface StoreKitBridge {
    /**
     * Registers the one place entitlements ever change. Swift is expected to
     * push the current set immediately and again on every `Transaction.updates`
     * — including ones that arrive with no purchase in flight, which is how a
     * family-shared or Ask-to-Buy unlock shows up.
     */
    fun observeEntitlements(onChange: (Set<String>) -> Unit)

    /** Store-formatted details; ids the App Store doesn't know are dropped. */
    fun products(ids: Set<String>, onResult: (List<BillingProduct>) -> Unit)

    fun purchase(productId: String, onResult: (PurchaseResult) -> Unit)

    /** Silent re-read of `Transaction.currentEntitlements`, pushed through [observeEntitlements]. */
    fun refresh(onComplete: () -> Unit)

    /** `AppStore.sync()`, which may prompt for the Apple Account password. */
    fun restore(onComplete: () -> Unit)
}

@Volatile
internal var installedStoreKitBridge: StoreKitBridge? = null
    private set

/**
 * Where the iOS app hands its StoreKit implementation to shared code.
 *
 * Call it from the app's entry points before anything resolves `Billing`. A
 * build that never calls it still runs — [com.coderwise.libs.billing.Billing.isAvailable]
 * simply reports false — which is what keeps the simulator, where there is no
 * store to buy from, from needing one.
 *
 * A free function rather than a member of an object called `StoreKit`, which
 * would collide with Apple's own `StoreKit` module in the one Swift file that
 * has to import both.
 */
fun installStoreKitBridge(bridge: StoreKitBridge) {
    installedStoreKitBridge = bridge
}
