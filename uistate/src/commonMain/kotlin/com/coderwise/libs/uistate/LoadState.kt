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
    val errorMessage: String? = null,
) {
    /** True if an error occurred (derived from [errorMessage] being non-null). */
    val isError: Boolean get() = errorMessage != null

    /** Returns a [LoadState] where loading has started and any previous error is cleared. */
    fun loading(): LoadState = copy(isLoading = true, errorMessage = null)

    /** Returns a [LoadState] where loading finished successfully. */
    fun loaded(): LoadState = copy(isLoading = false, errorMessage = null)

    /** Returns a [LoadState] where loading failed with [message]. */
    fun error(message: String?): LoadState = copy(isLoading = false, errorMessage = message)
}
