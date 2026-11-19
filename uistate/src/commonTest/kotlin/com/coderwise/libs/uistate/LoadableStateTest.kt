package com.coderwise.libs.uistate

import kotlin.test.Test
import kotlin.test.assertEquals

class LoadableStateTest {

    private data class State(
        val value: Int = 0,
        override val isLoading: Boolean = false,
        override val isError: Boolean = false,
        override val errorMessage: String? = null,
    ) : LoadableState<State> {
        override fun copyLoadState(isLoading: Boolean, isError: Boolean, errorMessage: String?) =
            copy(isLoading = isLoading, isError = isError, errorMessage = errorMessage)
    }

    @Test
    fun `loading clears a previous error`() {
        val state = State(value = 1).error("boom").loading()

        assertEquals(State(value = 1, isLoading = true), state)
    }

    @Test
    fun `error stops loading and keeps the message`() {
        val state = State().loading().error("boom")

        assertEquals(State(isError = true, errorMessage = "boom"), state)
    }

    @Test
    fun `loaded resets every load field and keeps the data`() {
        val state = State(value = 7).loading().loaded()

        assertEquals(State(value = 7), state)
    }
}
