package com.coderwise.libs.arch

import kotlin.test.Test
import kotlin.test.assertSame

class AppDispatchersTest {
    @Test
    fun `io is the same dispatcher every time`() {
        assertSame(AppDispatchers.io, AppDispatchers.io)
    }
}
