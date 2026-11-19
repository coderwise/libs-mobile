package com.coderwise.libs.uistate

/** Shared defaults for `StateFlow` sharing across ViewModels. */
object StateConfig {
    /**
     * Keep upstream flows active for this long after the last subscriber disappears, so a brief
     * UI teardown (a configuration change, say) does not restart them. Pass it to
     * `SharingStarted.WhileSubscribed(StateConfig.SubscriptionTimeoutMs)`.
     */
    const val SubscriptionTimeoutMs: Long = 5_000L
}
