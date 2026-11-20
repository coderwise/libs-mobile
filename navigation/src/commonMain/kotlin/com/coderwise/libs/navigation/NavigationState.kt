package com.coderwise.libs.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The app's navigation: a root back stack of top-level destinations (tabs, auth vs. main) and
 * one child back stack per root.
 *
 * One instance lives for the whole app (see [navigationModule]). [rememberAppNavigator] starts
 * it, and restores it after process death when given a `SavedStateConfiguration`; use neither
 * before that.
 */
@Stable
class NavigationState : Navigator {
    private var _rootBackStack: NavBackStack<NavKey>? = null
    val rootBackStack: NavBackStack<NavKey>
        get() = _rootBackStack ?: error("NavigationState not started. Call rememberAppNavigator(startRoot) first.")

    private val childBackStacks = mutableStateMapOf<NavKey, NavBackStack<NavKey>>()

    private val eventDispatcher = NavEventDispatcher()

    internal val isStarted: Boolean
        get() = _rootBackStack != null

    /** Starts at [startRoot]. Does nothing if already started or restored. */
    internal fun onStart(startRoot: NavKey) {
        if (_rootBackStack == null) {
            _rootBackStack = NavBackStack(startRoot)
        }
    }

    val currentRootKey: NavKey
        get() = rootBackStack.last()

    /** The child back stack of [rootKey], created empty on first use. */
    internal fun getBackStack(rootKey: NavKey): NavBackStack<NavKey> =
        childBackStacks.getOrPut(rootKey) { NavBackStack() }

    /**
     * The child back stack of [rootKey], holding at least [startDestination]. A stack dropped by
     * [replaceRoot] comes back here holding [startDestination] again.
     */
    internal fun getBackStack(rootKey: NavKey, startDestination: NavKey): NavBackStack<NavKey> =
        getBackStack(rootKey).also { if (it.isEmpty()) it.add(startDestination) }

    override val currentBackStack: NavBackStack<NavKey>
        get() = getBackStack(currentRootKey)

    override fun navigate(key: NavKey) {
        currentBackStack.add(key)
    }

    override fun navigateRoot(key: NavKey) {
        rootBackStack.add(key)
    }

    override fun replaceRoot(key: NavKey) {
        // Full reset: the new root starts fresh (signing out lands on Login, not on a stale
        // Sign Up screen left in its child stack).
        childBackStacks.clear()
        rootBackStack.clear()
        rootBackStack.add(key)
    }

    override fun switchRoot(key: NavKey) {
        if (currentRootKey == key) {
            val stack = currentBackStack
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            rootBackStack.remove(key)
            rootBackStack.add(key)
        }
    }

    override fun navigateUp() {
        navigateUp(currentRootKey)
    }

    /**
     * Pops the child back stack of [rootKey]. When that stack is down to its start
     * destination, pops [rootKey] itself off the root back stack, but only if it is the root
     * in front: a root that is not showing cannot go up.
     */
    internal fun navigateUp(rootKey: NavKey) {
        val stack = getBackStack(rootKey)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            rootBackStack.size > 1 && rootBackStack.last() == rootKey ->
                rootBackStack.removeAt(rootBackStack.lastIndex)
        }
    }

    internal fun addEventHandler(handler: Navigator.(NavEvent) -> Unit) = eventDispatcher.add(handler)

    internal fun removeEventHandler(handler: Navigator.(NavEvent) -> Unit) = eventDispatcher.remove(handler)

    override fun onEvent(event: NavEvent) {
        when (event) {
            GlobalNavEvent.Back -> navigateUp()
            else -> eventDispatcher.dispatch(this, event)
        }
    }

    internal fun snapshot(): NavigationSnapshot? = _rootBackStack?.let { root ->
        NavigationSnapshot(
            root = root.toList(),
            children = childBackStacks.map { (rootKey, stack) -> ChildBackStack(rootKey, stack.toList()) },
        )
    }

    /** Restores [snapshot] unless already started; returns whether it did. */
    internal fun restore(snapshot: NavigationSnapshot): Boolean {
        if (isStarted || snapshot.root.isEmpty()) return false
        _rootBackStack = NavBackStack(*snapshot.root.toTypedArray())
        childBackStacks.clear()
        snapshot.children.forEach { child ->
            childBackStacks[child.rootKey] = NavBackStack(*child.stack.toTypedArray())
        }
        return true
    }
}
