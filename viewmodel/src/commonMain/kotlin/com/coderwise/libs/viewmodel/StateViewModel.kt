package com.coderwise.libs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A ViewModel with one [uiState] and one entry point for the UI, [onEvent]:
 *
 * ```
 * class HomeViewModel(
 *     private val getItems: GetItemsUseCase,
 *     private val navEventSink: NavEventSink,
 * ) : StateViewModel<HomeUiState, HomeUiEvent>(HomeUiState()) {
 *     init { getItems().collectInto { copy(items = it) } }
 *
 *     override fun onEvent(event: HomeUiEvent) = when (event) {
 *         HomeUiEvent.Refresh -> launch { update { copy(isRefreshing = true) }; ... }
 *         HomeUiEvent.Back -> navEventSink.onEvent(GlobalNavEvent.Back)
 *     }
 * }
 * ```
 *
 * Navigation and load state belong to other libraries, so this class knows neither: inject the
 * navigation sink as usual and map failures to your state in [onError]. When what the screen draws
 * differs from what the ViewModel works on, use [ModelViewModel].
 */
abstract class StateViewModel<S : Any, E : Any>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)

    /** What the screen shows. Read-only; change it with [update]. */
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    /** Handles something the user did. The only way for the UI to reach the ViewModel. */
    abstract fun onEvent(event: E)

    /** The current state, for deciding what to do next. */
    protected val state: S get() = _uiState.value

    /** The state as a [StateAccess], to hand to a helper that works on it for this ViewModel. */
    protected val stateAccess: StateAccess<S> = FlowStateAccess(_uiState)

    /** Changes the state atomically: `update { copy(isLoading = true) }`. */
    protected fun update(transform: S.() -> S) {
        _uiState.update { it.transform() }
    }

    /**
     * Launches [block] in the ViewModel's scope. An exception other than cancellation goes to
     * [onError] instead of crashing the app.
     */
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launchSafely(::onError, block)

    /** Called when a [launch]ed block, or a flow passed to [collectInto], fails. Does nothing by default. */
    protected open fun onError(error: Throwable) {}

    /** Collects this flow for the ViewModel's lifetime, folding each value into the state with [reduce]. */
    protected fun <T> Flow<T>.collectInto(reduce: S.(T) -> S): Job =
        launch { collect { value -> update { reduce(value) } } }
}
