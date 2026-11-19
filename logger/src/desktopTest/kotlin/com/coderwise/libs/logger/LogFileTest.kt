package com.coderwise.libs.logger

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The JVM file, exercised on desktop because it is the same code Android runs
 * (both targets take it from `jvmSharedMain`) and desktop is where a real
 * filesystem is already available to tests.
 */
class LogFileTest {

    private val directoryPath = Path(SystemTemporaryDirectory, "logfile-${Random.nextLong().toULong()}")
    private val directory = directoryPath.toString()

    @AfterTest
    fun cleanUp() {
        listOf(FILE_NAME, "$FILE_NAME.1").forEach { SystemFileSystem.delete(Path(directoryPath, it), mustExist = false) }
        SystemFileSystem.delete(directoryPath, mustExist = false)
    }

    private fun open(maxBytes: Long) =
        requireNotNull(openLogFile(directory, FILE_NAME, maxBytes)) { "no log file under $directory" }

    @Test
    fun `lines written before a process death are there for the process after it`() {
        open(maxBytes = CAP).apply {
            append("first line")
            append("second line")
        }

        // What a relaunch sees: the same path opened fresh, holding nothing in memory.
        assertEquals(listOf("first line", "second line"), open(maxBytes = CAP).lines())
    }

    @Test
    fun `passing the cap rotates, and the rotated generation still reads oldest first`() {
        val file = open(maxBytes = 32)

        file.append("a".repeat(40))
        file.append("after rotation")

        assertEquals(listOf("a".repeat(40), "after rotation"), file.lines())
    }

    @Test
    fun `only one rotated generation is kept`() {
        val file = open(maxBytes = 32)

        file.append("a".repeat(40))
        file.append("b".repeat(40))
        file.append("c".repeat(40))

        assertEquals(listOf("b".repeat(40), "c".repeat(40)), file.lines())
    }

    private companion object {
        const val FILE_NAME = "app.log"
        const val CAP = 1024L
    }
}
