package com.coderwise.libs.uistate

/**
 * Encapsulates loading and error states (`isLoading`, `isError`, `errorMessage`) for UI states
 * without requiring interface inheritance or boilerplate overrides.
 *
 * ```
 * data class ListUiState(
 *     val items: List<Item> = emptyList(),
 *     val loadState: LoadState = LoadState(),
 * )
 *
 * _uiState.update { it.copy(loadState = it.loadState.loading()) }
 * ```
 */
data class LoadState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Returns a [LoadState] where loading has started and any previous error is cleared. */
    fun loading(): LoadState = copy(isLoading = true, isError = false, errorMessage = null)

    /** Returns a [LoadState] where loading finished successfully. */
    fun loaded(): LoadState = copy(isLoading = false, isError = false, errorMessage = null)

    /** Returns a [LoadState] where loading failed with [message]. */
    fun error(message: String?): LoadState = copy(isLoading = false, isError = true, errorMessage = message)
}
