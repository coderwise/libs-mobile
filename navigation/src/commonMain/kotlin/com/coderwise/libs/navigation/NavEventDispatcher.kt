package com.coderwise.libs.navigation

/**
 * Registered navigation event handlers, kept apart from back-stack management.
 *
 * [dispatch] is a broadcast: every handler sees every event, so a handler ignores the events
 * it does not own. Handlers are compared by identity.
 */
internal class NavEventDispatcher {
    private val handlers = mutableListOf<Navigator.(NavEvent) -> Unit>()

    fun add(handler: Navigator.(NavEvent) -> Unit) {
        if (handlers.none { it === handler }) handlers.add(handler)
    }

    fun remove(handler: Navigator.(NavEvent) -> Unit) {
        handlers.removeAll { it === handler }
    }

    fun dispatch(navigator: Navigator, event: NavEvent) {
        // Copy first: a handler may navigate, and navigating can dispose a composable that
        // removes its own handler while we are still iterating.
        handlers.toList().forEach { handler -> handler(navigator, event) }
    }
}
