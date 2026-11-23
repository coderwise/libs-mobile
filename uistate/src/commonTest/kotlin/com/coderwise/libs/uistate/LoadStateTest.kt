package com.coderwise.libs.uistate

import kotlin.test.Test
import kotlin.test.assertEquals

class LoadStateTest {

    private data class State(
        val value: Int = 0,
        val loadState: LoadState = LoadState(),
    )

    @Test
    fun `loading clears a previous error`() {
        var state = State(value = 1).copy(loadState = LoadState(isError = true, errorMessage = "boom"))
        state = state.copy(loadState = state.loadState.loading())

        assertEquals(State(value = 1, loadState = LoadState(isLoading = true)), state)
    }

    @Test
    fun `error stops loading and keeps the message`() {
        var state = State().copy(loadState = LoadState().loading())
        state = state.copy(loadState = state.loadState.error("boom"))

        assertEquals(State(loadState = LoadState(isError = true, errorMessage = "boom")), state)
    }

    @Test
    fun `loaded resets every load field and keeps the data`() {
        var state = State(value = 7).copy(loadState = LoadState().loading())
        state = state.copy(loadState = state.loadState.loaded())

        assertEquals(State(value = 7), state)
    }

    @Test
    fun `state update with loadState helpers`() {
        var state = State(value = 5)
        state = state.copy(loadState = state.loadState.loading())
        assertEquals(true, state.loadState.isLoading)

        state = state.copy(loadState = state.loadState.error("failed"))
        assertEquals(true, state.loadState.isError)
        assertEquals("failed", state.loadState.errorMessage)

        state = state.copy(loadState = state.loadState.loaded())
        assertEquals(false, state.loadState.isLoading)
        assertEquals(false, state.loadState.isError)
        assertEquals(null, state.loadState.errorMessage)
    }
}
