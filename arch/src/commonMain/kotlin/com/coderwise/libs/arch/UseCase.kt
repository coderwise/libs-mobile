package com.coderwise.libs.arch

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * A suspending use case taking [P] and returning [R], run on [dispatcher].
 *
 * The default, [Dispatchers.Default], suits CPU work. Pass an I/O dispatcher for use cases
 * that mostly wait on disk or network, and a test dispatcher in tests.
 */
abstract class SuspendUseCase<in P, out R>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(params: P): R = withContext(dispatcher) { execute(params) }

    protected abstract suspend fun execute(params: P): R
}

/** A use case taking [P] and returning a [Flow] of [R], collected upstream on [dispatcher]. */
abstract class FlowUseCase<in P, out R>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    operator fun invoke(params: P): Flow<R> = execute(params).flowOn(dispatcher)

    protected abstract fun execute(params: P): Flow<R>
}

/** A suspending use case with no parameters, run on [dispatcher]. */
abstract class SuspendResultUseCase<out R>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(): R = withContext(dispatcher) { execute() }

    protected abstract suspend fun execute(): R
}

/** A use case with no parameters returning a [Flow] of [R], collected upstream on [dispatcher]. */
abstract class FlowResultUseCase<out R>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    operator fun invoke(): Flow<R> = execute().flowOn(dispatcher)

    protected abstract fun execute(): Flow<R>
}

/** A synchronous use case taking [P] and returning [R], run on the caller's thread. */
abstract class UseCase<in P, out R> {
    abstract operator fun invoke(params: P): R
}

/** A synchronous use case with no parameters, run on the caller's thread. */
abstract class ResultUseCase<out R> {
    abstract operator fun invoke(): R
}
