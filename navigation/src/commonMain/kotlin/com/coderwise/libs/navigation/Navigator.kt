package com.coderwise.libs.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Marker for anything dispatched through [NavEventHandler.onEvent]. Apps declare their own
 * events as subtypes, typically one sealed interface per feature.
 */
interface NavEvent

/** Events every [Navigator] handles itself, without a registered handler. */
sealed interface GlobalNavEvent : NavEvent {
    /** Same as [ScreenNavigator.navigateUp]. */
    data object Back : GlobalNavEvent
}

fun interface NavEventHandler {
    fun onEvent(event: NavEvent)
}

/**
 * The navigation surface a single screen needs: go to a destination, go back, or emit an
 * event for the hosting nav graph to act on. It cannot reset the app's root back stacks;
 * that is [Navigator]'s job.
 */
@Stable
interface ScreenNavigator : NavEventHandler {
    fun navigate(key: NavKey)
    fun navigateUp()
}

/**
 * Full navigation control: [ScreenNavigator] plus root-graph management and the active back
 * stack. Used by the app shell and by the nav-graph composables that host a `NavDisplay`.
 */
@Stable
interface Navigator : ScreenNavigator {
    val currentBackStack: NavBackStack<NavKey>

    /** Pushes [key] onto the root back stack. */
    fun navigateRoot(key: NavKey)

    /** Drops every back stack and starts again at [key], e.g. on sign-out. */
    fun replaceRoot(key: NavKey)

    /**
     * Brings root [key] to the front, keeping its child back stack. If it is already in
     * front, pops its child back stack to its start destination (tapping the current tab).
     */
    fun switchRoot(key: NavKey)
}
