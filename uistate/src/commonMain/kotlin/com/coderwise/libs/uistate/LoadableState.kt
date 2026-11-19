package com.coderwise.libs.uistate

/**
 * Shared loading/error contract for UI states. Implementing it lets a state reuse the
 * [loading], [loaded] and [error] transitions instead of each ViewModel declaring its own.
 *
 * ```
 * data class ListUiState(
 *     val items: List<Item> = emptyList(),
 *     override val isLoading: Boolean = false,
 *     override val isError: Boolean = false,
 *     override val errorMessage: String? = null,
 * ) : LoadableState<ListUiState> {
 *     override fun copyLoadState(isLoading: Boolean, isError: Boolean, errorMessage: String?) =
 *         copy(isLoading = isLoading, isError = isError, errorMessage = errorMessage)
 * }
 *
 * _uiState.update { it.loading() }
 * ```
 */
interface LoadableState<T : LoadableState<T>> {
    val isLoading: Boolean
    val isError: Boolean
    val errorMessage: String?

    /** Returns a copy of this state with the loading fields replaced. */
    fun copyLoadState(
        isLoading: Boolean = this.isLoading,
        isError: Boolean = this.isError,
        errorMessage: String? = this.errorMessage,
    ): T
}

/** Loading has started; any previous error is cleared. */
fun <T : LoadableState<T>> T.loading(): T =
    copyLoadState(isLoading = true, isError = false, errorMessage = null)

/** Loading finished successfully. */
fun <T : LoadableState<T>> T.loaded(): T =
    copyLoadState(isLoading = false, isError = false, errorMessage = null)

/** Loading failed with [message]. */
fun <T : LoadableState<T>> T.error(message: String?): T =
    copyLoadState(isLoading = false, isError = true, errorMessage = message)
