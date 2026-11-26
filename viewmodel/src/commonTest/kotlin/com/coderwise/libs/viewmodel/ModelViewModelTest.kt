package com.coderwise.libs.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ModelViewModelTest {

    private data class Model(val seconds: Int = 0, val error: String? = null)

    private data class Ui(val label: String)

    private sealed interface Event {
        data object Tick : Event
        data object Fail : Event
    }

    private class ClockViewModel(
        source: MutableSharedFlow<Int> = MutableSharedFlow(),
    ) : ModelViewModel<Model, Ui, Event>(Model(), { Ui(it.error ?: "${it.seconds}s") }) {
        init {
            source.collectInto { copy(seconds = it) }
        }

        val currentSeconds: Int get() = model.seconds

        override fun onEvent(event: Event) = when (event) {
            Event.Tick -> update { copy(seconds = seconds + 1) }
            Event.Fail -> { launch { error("boom") }; Unit }
        }

        override fun onError(error: Throwable) = update { copy(error = error.message) }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts with the initial model mapped`() {
        assertEquals(Ui("0s"), ClockViewModel().uiState.value)
    }

    @Test
    fun `maps each model change to the ui state`() {
        val viewModel = ClockViewModel()
        viewModel.onEvent(Event.Tick)
        viewModel.onEvent(Event.Tick)
        assertEquals(2, viewModel.currentSeconds)
        assertEquals(Ui("2s"), viewModel.uiState.value)
    }

    @Test
    fun `collectInto folds a flow into the model`() {
        val source = MutableSharedFlow<Int>()
        val viewModel = ClockViewModel(source)
        kotlinx.coroutines.test.runTest { source.emit(7) }
        assertEquals(Ui("7s"), viewModel.uiState.value)
    }

    @Test
    fun `a failed launch goes to onError`() {
        val viewModel = ClockViewModel()
        viewModel.onEvent(Event.Fail)
        assertEquals(Ui("boom"), viewModel.uiState.value)
    }
}
