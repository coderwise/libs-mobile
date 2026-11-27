package com.coderwise.libs.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultShellTest {
    private val root = createTempDirectory("shell").toString()
    private val shell = createShell()

    @Test fun `write creates parent directories and read returns the text`() = runTest {
        shell.writeText("$root/a/b/file.txt", "hello\n")
        assertEquals("hello\n", shell.readText("$root/a/b/file.txt"))
    }

    @Test fun `read of a missing file or a directory is null`() = runTest {
        shell.writeText("$root/dir/f.txt", "x")
        assertNull(shell.readText("$root/missing.txt"))
        assertNull(shell.readText("$root/dir"))
    }

    @Test fun `exists and listDirs`() = runTest {
        shell.writeText("$root/one/f.txt", "x")
        shell.writeText("$root/two/f.txt", "x")
        shell.writeText("$root/loose.txt", "x")
        assertTrue(shell.exists("$root/one"))
        assertFalse(shell.exists("$root/nope"))
        assertEquals(listOf("one", "two"), shell.listDirs(root).sorted())
        assertEquals(emptyList(), shell.listDirs("$root/nope"))
    }

    @Test fun `run captures output and exit code`() = runTest {
        val r = shell.run(root, listOf("echo", "hi"))
        assertEquals(ShellResult(0, "hi\n"), r)
    }

    @Test fun `cancelling a run kills the process`() = runBlocking {
        val marker = "$root/marker"
        val job = launch(Dispatchers.Default) { shell.run(root, listOf("sh", "-c", "sleep 3; touch $marker"), timeoutSeconds = 30) }
        delay(500)
        job.cancelAndJoin()
        delay(3500)
        assertFalse(shell.exists(marker))
    }

    @Test fun `listEntries tells files from directories`() = runTest {
        shell.writeText("$root/d/f.txt", "x")
        shell.writeText("$root/top.txt", "x")
        assertEquals(setOf(ShellEntry("d", true), ShellEntry("top.txt", false)), shell.listEntries(root).toSet())
        assertEquals(emptyList(), shell.listEntries("$root/top.txt"))
    }

    @Test fun `canonicalPath resolves dots and is null for missing paths`() = runTest {
        shell.writeText("$root/a/f.txt", "x")
        val real = shell.canonicalPath(root)!!
        assertEquals("$real/a/f.txt", shell.canonicalPath("$root/a/../a/f.txt"))
        assertNull(shell.canonicalPath("$root/missing"))
    }
}
