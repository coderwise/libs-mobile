package com.coderwise.libs.logger

import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentLogWriterTest {

    private fun RecentLogWriter.write(message: String) =
        log(Severity.Info, message, "TestTag", null)

    @Test
    fun `a line carries its severity and tag`() {
        val writer = RecentLogWriter(capacity = 4)

        writer.write("collecting location updates")

        val line = writer.snapshot().single()
        assertTrue(line.endsWith("I/TestTag: collecting location updates"), line)
    }

    @Test
    fun `only the newest lines survive once the ring wraps`() {
        val writer = RecentLogWriter(capacity = 3)

        repeat(5) { writer.write("line $it") }

        val messages = writer.snapshot().map { it.substringAfter("I/TestTag: ") }
        assertEquals(listOf("line 2", "line 3", "line 4"), messages)
    }

    @Test
    fun `a throwable is kept with the line it belongs to`() {
        val writer = RecentLogWriter(capacity = 2)

        writer.log(Severity.Warn, "provider error", "TestTag", IllegalStateException("denied"))

        assertTrue(writer.snapshot().single().contains("denied"))
    }

    @Test
    fun `an empty buffer reads as no lines rather than blank ones`() {
        assertEquals(emptyList(), RecentLogWriter(capacity = 8).snapshot())
    }
}
