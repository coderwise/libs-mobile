package com.coderwise.libs.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import org.koin.compose.koinInject

/**
 * Starts [navigationState] at [startRoot] and returns it, for the app shell's root `NavDisplay`.
 *
 * Pass [savedStateConfiguration] to keep every back stack across process death on Android. Its
 * `serializersModule` must register each [NavKey] type under `polymorphic(NavKey::class)`.
 * Without it, navigation restarts at [startRoot] after process death.
 *
 * [onEvent] receives every [NavEvent] that is not a [GlobalNavEvent], while this is composed.
 */
@Composable
fun rememberAppNavigator(
    startRoot: NavKey,
    savedStateConfiguration: SavedStateConfiguration? = null,
    navigationState: NavigationState = koinInject(),
    onEvent: Navigator.(NavEvent) -> Unit = {},
): NavigationState {
    if (savedStateConfiguration != null) {
        rememberSaveable(saver = navigationStateSaver(navigationState, savedStateConfiguration)) {
            navigationState
        }
    }
    remember(navigationState, startRoot) { navigationState.onStart(startRoot) }
    RegisterEventHandler(navigationState, onEvent)
    return navigationState
}

/**
 * A [Navigator] bound to the child back stack of [rootKey], for a root's nested `NavDisplay`.
 *
 * Pass the root entry's own key as [rootKey], not `currentRootKey`: during a root transition or
 * a predictive back gesture two roots compose at once, and each must keep pushing to its own
 * stack. The stack is looked up on every access, so it stays right after [Navigator.replaceRoot]
 * drops and recreates it.
 */
@Composable
fun rememberChildNavigator(
    rootKey: NavKey,
    startDestination: NavKey,
    navigationState: NavigationState = koinInject(),
    onEvent: Navigator.(NavEvent) -> Unit = {},
): Navigator {
    val navigator = remember(navigationState, rootKey, startDestination) {
        ChildNavigator(navigationState, rootKey, startDestination)
    }
    RegisterEventHandler(navigationState, onEvent)
    return navigator
}

/**
 * Registers one stable wrapper per [navigationState] that always calls the latest [onEvent].
 * A new lambda on recomposition does not re-register, and the handler removed on dispose is
 * the one that was added.
 */
@Composable
private fun RegisterEventHandler(
    navigationState: NavigationState,
    onEvent: Navigator.(NavEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    DisposableEffect(navigationState) {
        val handler: Navigator.(NavEvent) -> Unit = { event -> currentOnEvent(event) }
        navigationState.addEventHandler(handler)
        onDispose { navigationState.removeEventHandler(handler) }
    }
}

internal class ChildNavigator(
    private val state: NavigationState,
    private val rootKey: NavKey,
    private val startDestination: NavKey,
) : Navigator by state {

    override val currentBackStack: NavBackStack<NavKey>
        get() = state.getBackStack(rootKey, startDestination)

    override fun navigate(key: NavKey) {
        currentBackStack.add(key)
    }

    override fun navigateUp() {
        // Seed first, so a stack recreated after replaceRoot still holds its start destination.
        state.getBackStack(rootKey, startDestination)
        state.navigateUp(rootKey)
    }

    override fun onEvent(event: NavEvent) {
        when (event) {
            GlobalNavEvent.Back -> navigateUp()
            else -> state.onEvent(event)
        }
    }
}

private fun navigationStateSaver(
    target: NavigationState,
    configuration: SavedStateConfiguration,
): Saver<NavigationState, SavedState> = Saver(
    save = { state ->
        state.snapshot()?.let { encodeToSavedState(NavigationSnapshot.serializer(), it, configuration) }
    },
    restore = { saved ->
        target.restore(decodeFromSavedState(NavigationSnapshot.serializer(), saved, configuration))
        target
    },
)
