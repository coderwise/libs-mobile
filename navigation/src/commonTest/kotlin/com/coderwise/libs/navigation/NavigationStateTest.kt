package com.coderwise.libs.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable data object Home : NavKey
@Serializable data object Profile : NavKey
@Serializable data object Login : NavKey
@Serializable data object Feed : NavKey
@Serializable data class Detail(val id: String) : NavKey

private data object Opened : NavEvent

class NavigationStateTest {

    private fun started(root: NavKey = Home) = NavigationState().apply { onStart(root) }

    @Test
    fun `using it before onStart fails with a clear message`() {
        val error = assertFailsWith<IllegalStateException> { NavigationState().navigate(Feed) }
        assertTrue(error.message!!.contains("onStart"))
    }

    @Test
    fun `onStart only applies once`() {
        val state = started(Home)
        state.onStart(Login)

        assertEquals(listOf<NavKey>(Home), state.rootBackStack.toList())
    }

    @Test
    fun `navigate pushes onto the current root's child stack`() {
        val state = started()
        state.navigate(Feed)
        state.navigate(Detail("1"))

        assertEquals(listOf(Feed, Detail("1")), state.getBackStack(Home).toList())
    }

    @Test
    fun `switchRoot keeps each root's child stack`() {
        val state = started()
        state.navigate(Detail("1"))
        state.switchRoot(Profile)
        state.navigate(Detail("2"))
        state.switchRoot(Home)

        assertEquals(listOf(Profile, Home), state.rootBackStack.toList())
        assertEquals(listOf<NavKey>(Detail("1")), state.currentBackStack.toList())
        assertEquals(listOf<NavKey>(Detail("2")), state.getBackStack(Profile).toList())
    }

    @Test
    fun `switchRoot to the current root pops to its start destination`() {
        val state = started()
        state.getBackStack(Home, Feed)
        state.navigate(Detail("1"))
        state.navigate(Detail("2"))
        state.switchRoot(Home)

        assertEquals(listOf<NavKey>(Feed), state.currentBackStack.toList())
    }

    @Test
    fun `replaceRoot drops every back stack`() {
        val state = started()
        state.navigate(Detail("1"))
        state.navigateRoot(Profile)
        state.replaceRoot(Login)

        assertEquals(listOf<NavKey>(Login), state.rootBackStack.toList())
        assertTrue(state.getBackStack(Home).isEmpty())
    }

    @Test
    fun `navigateUp pops the child stack and then the root`() {
        val state = started()
        state.getBackStack(Home, Feed)
        state.navigate(Detail("1"))
        state.navigateRoot(Profile)
        state.getBackStack(Profile, Detail("p"))

        state.navigateUp()
        assertEquals(listOf<NavKey>(Home), state.rootBackStack.toList())

        state.navigateUp()
        assertEquals(listOf<NavKey>(Feed), state.currentBackStack.toList())

        state.navigateUp()
        assertEquals(listOf<NavKey>(Feed), state.currentBackStack.toList())
        assertEquals(listOf<NavKey>(Home), state.rootBackStack.toList())
    }

    @Test
    fun `navigateUp on a root that is not in front leaves the root stack alone`() {
        val state = started()
        state.navigateRoot(Profile)

        state.navigateUp(Home)

        assertEquals(listOf(Home, Profile), state.rootBackStack.toList())
    }

    @Test
    fun `Back is handled without a handler and other events reach every handler`() {
        val state = started()
        state.navigate(Feed)
        state.navigate(Detail("1"))
        val seen = mutableListOf<NavEvent>()
        val handler: Navigator.(NavEvent) -> Unit = { seen += it }
        state.addEventHandler(handler)
        state.addEventHandler(handler)

        state.onEvent(GlobalNavEvent.Back)
        state.onEvent(Opened)

        assertEquals(listOf<NavKey>(Feed), state.currentBackStack.toList())
        assertEquals(listOf<NavEvent>(Opened), seen)

        state.removeEventHandler(handler)
        state.onEvent(Opened)
        assertEquals(listOf<NavEvent>(Opened), seen)
    }

    @Test
    fun `a handler may remove itself while events are dispatched`() {
        val state = started()
        var calls = 0
        lateinit var handler: Navigator.(NavEvent) -> Unit
        handler = {
            calls++
            state.removeEventHandler(handler)
        }
        state.addEventHandler(handler)

        state.onEvent(Opened)
        state.onEvent(Opened)

        assertEquals(1, calls)
    }

    @Test
    fun `snapshot and restore round-trip every back stack`() {
        val state = started()
        state.navigate(Detail("1"))
        state.switchRoot(Profile)
        state.navigate(Detail("2"))
        val snapshot = state.snapshot()!!

        val restored = NavigationState()
        assertTrue(restored.restore(snapshot))
        restored.onStart(Login)

        assertEquals(listOf(Home, Profile), restored.rootBackStack.toList())
        assertEquals(listOf<NavKey>(Detail("1")), restored.getBackStack(Home).toList())
        assertEquals(listOf<NavKey>(Detail("2")), restored.currentBackStack.toList())
    }

    @Test
    fun `restore does nothing once started`() {
        val snapshot = started(Profile).snapshot()!!
        val state = started(Home)

        assertFalse(state.restore(snapshot))
        assertEquals(listOf<NavKey>(Home), state.rootBackStack.toList())
    }
}
