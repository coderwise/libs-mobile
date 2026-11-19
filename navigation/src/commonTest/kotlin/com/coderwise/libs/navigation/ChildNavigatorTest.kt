package com.coderwise.libs.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

private data object Tapped : NavEvent

class ChildNavigatorTest {

    private val state = NavigationState().apply { onStart(Home) }

    @Test
    fun `starts its stack at the start destination`() {
        val child = ChildNavigator(state, Home, Feed)

        assertEquals(listOf<NavKey>(Feed), child.currentBackStack.toList())
    }

    @Test
    fun `pushes to its own root even when another root is in front`() {
        val home = ChildNavigator(state, Home, Feed)
        state.navigateRoot(Profile)

        home.navigate(Detail("1"))

        assertEquals(listOf(Feed, Detail("1")), state.getBackStack(Home).toList())
        assertEquals(emptyList(), state.getBackStack(Profile).toList())
    }

    @Test
    fun `uses the recreated stack after replaceRoot`() {
        val home = ChildNavigator(state, Home, Feed)
        home.navigate(Detail("1"))

        state.replaceRoot(Home)
        home.navigate(Detail("2"))

        assertEquals(listOf(Feed, Detail("2")), state.getBackStack(Home).toList())
        assertEquals(listOf(Feed, Detail("2")), home.currentBackStack.toList())
    }

    @Test
    fun `a child event handler navigates on the child stack while another root is in front`() {
        val home = ChildNavigator(state, Home, Feed)
        state.addEventHandler(eventHandler(home) { navigate(Detail("from-event")) })
        state.navigateRoot(Profile)

        state.onEvent(Tapped)

        assertEquals(listOf(Feed, Detail("from-event")), state.getBackStack(Home).toList())
        assertEquals(emptyList(), state.getBackStack(Profile).toList())
    }

    @Test
    fun `an app-level handler runs on the dispatching navigator`() {
        var receiver: Navigator? = null
        state.addEventHandler(eventHandler(null) { receiver = this })

        state.onEvent(Tapped)

        assertEquals<Navigator?>(state, receiver)
    }

    @Test
    fun `Back pops its own stack and then its root`() {
        state.navigateRoot(Profile)
        val profile = ChildNavigator(state, Profile, Detail("p"))
        profile.navigate(Detail("q"))

        profile.onEvent(GlobalNavEvent.Back)
        assertEquals(listOf<NavKey>(Detail("p")), profile.currentBackStack.toList())

        profile.onEvent(GlobalNavEvent.Back)
        assertEquals(listOf<NavKey>(Home), state.rootBackStack.toList())
    }
}
