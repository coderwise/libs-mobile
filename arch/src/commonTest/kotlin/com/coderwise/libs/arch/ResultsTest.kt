package com.coderwise.libs.arch

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ResultsTest {
    @Test fun `returns the value as a success`() {
        assertEquals(Result.success(3), resultOf { 1 + 2 })
    }

    @Test fun `returns an exception as a failure`() {
        val boom = IllegalStateException("boom")
        assertSame(boom, resultOf<Int> { throw boom }.exceptionOrNull())
    }

    @Test fun `lets cancellation through`() {
        assertFailsWith<CancellationException> { resultOf<Int> { throw CancellationException("stop") } }
    }
}
