package com.coderwise.libs.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StateViewModelTest {

    private data class State(val count: Int = 0, val error: String? = null)

    private sealed interface Event {
        data object Increment : Event
        data object Fail : Event
    }

    private class CounterViewModel(
        private val source: MutableSharedFlow<Int> = MutableSharedFlow(),
    ) : StateViewModel<State, Event>(State()) {
        init {
            source.collectInto { copy(count = it) }
        }

        val currentCount: Int get() = state.count

        override fun onEvent(event: Event) = when (event) {
            Event.Increment -> update { copy(count = count + 1) }
            Event.Fail -> { launch { error("boom") }; Unit }
        }

        override fun onError(error: Throwable) = update { copy(error = error.message) }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts with the initial state`() {
        assertEquals(State(), CounterViewModel().uiState.value)
    }

    @Test
    fun `update changes the state and state reads it`() {
        val viewModel = CounterViewModel()
        viewModel.onEvent(Event.Increment)
        viewModel.onEvent(Event.Increment)
        assertEquals(2, viewModel.uiState.value.count)
        assertEquals(2, viewModel.currentCount)
    }

    @Test
    fun `a flow passed to collectInto is folded into the state`() = runTest {
        val source = MutableSharedFlow<Int>()
        val viewModel = CounterViewModel(source)
        source.emit(7)
        assertEquals(7, viewModel.uiState.value.count)
    }

    @Test
    fun `an exception in launch goes to onError instead of crashing`() {
        val viewModel = CounterViewModel()
        viewModel.onEvent(Event.Fail)
        assertEquals("boom", viewModel.uiState.value.error)
    }

    @Test
    fun `a failing flow goes to onError`() {
        val viewModel = object : StateViewModel<State, Event>(State()) {
            init {
                flow<Int> { throw IllegalStateException("bad flow") }.collectInto { copy(count = it) }
            }

            override fun onEvent(event: Event) = Unit
            override fun onError(error: Throwable) = update { copy(error = error.message) }
        }
        assertEquals("bad flow", viewModel.uiState.value.error)
    }
}
