package com.coderwise.libs.arch

import kotlin.coroutines.cancellation.CancellationException

/** Like `runCatching`, but lets coroutine cancellation through instead of turning it into a failed result. */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
