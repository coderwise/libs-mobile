package com.coderwise.libs.arch

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
class UseCaseTest {

    private class Double(dispatcher: CoroutineDispatcher) : SuspendUseCase<Int, Int>(dispatcher) {
        var ranOn: CoroutineDispatcher? = null
        override suspend fun execute(params: Int): Int {
            ranOn = currentCoroutineContext()[CoroutineDispatcher]
            return params * 2
        }
    }

    private class CountTo(dispatcher: CoroutineDispatcher) : FlowUseCase<Int, Int>(dispatcher) {
        var ranOn: CoroutineDispatcher? = null
        override fun execute(params: Int): Flow<Int> = flow {
            ranOn = currentCoroutineContext()[CoroutineDispatcher]
            for (i in 1..params) emit(i)
        }
    }

    private class Answer(dispatcher: CoroutineDispatcher) : SuspendResultUseCase<Int>(dispatcher) {
        override suspend fun execute() = 42
    }

    private class Constant(dispatcher: CoroutineDispatcher) : FlowResultUseCase<String>(dispatcher) {
        override fun execute(): Flow<String> = flow { emit("a"); emit("b") }
    }

    @Test
    fun `suspend use case runs on its dispatcher`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = Double(dispatcher)

        assertEquals(6, useCase(3))
        assertSame(dispatcher, useCase.ranOn)
    }

    @Test
    fun `flow use case collects upstream on its dispatcher`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = CountTo(dispatcher)

        assertEquals(listOf(1, 2, 3), useCase(3).toList())
        assertSame(dispatcher, useCase.ranOn)
    }

    private class Ping(dispatcher: CoroutineDispatcher) : SuspendUseCase<Unit, String>(dispatcher) {
        override suspend fun execute(params: Unit) = "pong"
    }

    private class Ticks(dispatcher: CoroutineDispatcher) : FlowUseCase<Unit, Int>(dispatcher) {
        override fun execute(params: Unit): Flow<Int> = flow { emit(1); emit(2) }
    }

    @Test
    fun `Unit use cases can be invoked without an argument`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertEquals("pong", Ping(dispatcher)())
        assertEquals(listOf(1, 2), Ticks(dispatcher)().toList())
    }

    @Test
    fun `parameterless variants return their results`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertEquals(42, Answer(dispatcher)())
        assertEquals(listOf("a", "b"), Constant(dispatcher)().toList())
    }
}
