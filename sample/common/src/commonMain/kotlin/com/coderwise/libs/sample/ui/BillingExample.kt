package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.billing.Billing
import com.coderwise.libs.billing.UnavailableBilling
import kotlinx.coroutines.launch

private const val PRODUCT_ID = "premium_lifetime"

@Composable
internal fun BillingExample() {
    // The implementation every build without a store gets, and the one an iOS app that has not
    // handed over its StoreKit bridge falls back to. A real Billing comes from `billingModule`,
    // which needs a DI graph and a product configured in a store -- neither of which a sample
    // can ship -- so this is the half of the contract that can be shown without them.
    val billing: Billing = UnavailableBilling
    val owned by billing.entitlements.collectAsState()
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf(emptyList<String>()) }

    fun record(line: String) {
        log = (listOf(line) + log).take(10)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoSection("State") {
            ReadoutRow("isAvailable", billing.isAvailable.toString())
            ReadoutRow("entitlements", owned.toString())
        }
        DemoSection("Calls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { record("refresh() → ${billing.refresh()}") } }) {
                    Text("refresh()")
                }
                OutlinedButton(onClick = {
                    scope.launch { record("products() → ${billing.products(setOf(PRODUCT_ID))}") }
                }) { Text("products()") }
                Button(onClick = {
                    scope.launch { record("purchase() → ${billing.purchase(PRODUCT_ID)}") }
                }) { Text("purchase(\"$PRODUCT_ID\")") }
                OutlinedButton(onClick = { scope.launch { record("restore() → ${billing.restore()}") } }) {
                    Text("restore()")
                }
            }
        }
        if (log.isNotEmpty()) {
            DemoSection("Returned") { OutputBox(log.joinToString("\n")) }
        }
        PlatformNote(
            "Every call stays callable and inert where there is no store, so an app needs no " +
                "platform branches. refresh() and restore() return false — \"nobody answered\" — " +
                "rather than an empty set that would read as \"you own nothing\", which matters " +
                "to anything caching an unlock for offline use. Wiring a real one: Android adds " +
                "billingModule to its Koin graph with androidContext() set, for Play Billing; " +
                "iOS implements StoreKitBridge in Swift (StoreKit 2 is Swift-only) and passes " +
                "it to installStoreKitBridge() before anything resolves Billing. Desktop and " +
                "the web never have a store."
        )
    }
}
