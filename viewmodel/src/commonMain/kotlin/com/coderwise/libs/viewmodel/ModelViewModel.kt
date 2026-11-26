package com.coderwise.libs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * A [StateViewModel] whose state is not what the screen draws.
 *
 * The ViewModel works on a model [M], in domain terms, which may be `internal`, hold raw data, and
 * be shared with helpers. [uiState] is that model mapped to the public [S] by [toUiState], so the
 * formatting happens in one place:
 *
 * ```
 * class RadarViewModel(...) : ModelViewModel<RadarModel, RadarUiState, RadarUiEvent>(
 *     initialModel = RadarModel(),
 *     toUiState = RadarModel::toUiState,
 * )
 * ```
 *
 * Use [StateViewModel] when the two would be the same type.
 */
abstract class ModelViewModel<M : Any, S : Any, E : Any>(
    initialModel: M,
    toUiState: (M) -> S,
) : ViewModel() {

    private val _model = MutableStateFlow(initialModel)

    /** What the screen shows: the model, mapped by `toUiState` whenever it changes. */
    val uiState: StateFlow<S> = _model
        .map(toUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(initialModel))

    /** Handles something the user did. The only way for the UI to reach the ViewModel. */
    abstract fun onEvent(event: E)

    /** The current model, for deciding what to do next. */
    protected val model: M get() = _model.value

    /** The state as a [StateAccess], to hand to a helper that works on it for this ViewModel. */
    protected val stateAccess: StateAccess<M> = FlowStateAccess(_model)

    /** Changes the model atomically: `update { copy(isLoading = true) }`. */
    protected fun update(transform: M.() -> M) {
        _model.update { it.transform() }
    }

    /**
     * Launches [block] in the ViewModel's scope. An exception other than cancellation goes to
     * [onError] instead of crashing the app.
     */
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launchSafely(::onError, block)

    /** Called when a [launch]ed block, or a flow passed to [collectInto], fails. Does nothing by default. */
    protected open fun onError(error: Throwable) {}

    /** Collects this flow for the ViewModel's lifetime, folding each value into the model with [reduce]. */
    protected fun <T> Flow<T>.collectInto(reduce: M.(T) -> M): Job =
        launch { collect { value -> update { reduce(value) } } }
}
