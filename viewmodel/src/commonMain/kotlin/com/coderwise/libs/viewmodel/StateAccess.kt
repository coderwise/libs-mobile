package com.coderwise.libs.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The ViewModel's state, handed to a helper (a controller, a delegate) that reads and changes it on
 * the ViewModel's behalf without being able to replace it. Get it from `stateAccess`.
 */
interface StateAccess<S> {
    /** The current state. */
    val value: S

    /** The state as a flow, for a helper that reacts to it. */
    val flow: StateFlow<S>

    /** Changes the state atomically: `update { it.copy(isLoading = true) }`. */
    fun update(transform: (S) -> S)
}

internal class FlowStateAccess<S>(private val source: MutableStateFlow<S>) : StateAccess<S> {
    override val value: S get() = source.value
    override val flow: StateFlow<S> get() = source
    override fun update(transform: (S) -> S) = source.update(transform)
}
