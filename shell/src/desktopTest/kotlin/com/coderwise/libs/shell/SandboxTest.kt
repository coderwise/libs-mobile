package com.coderwise.libs.shell

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SandboxTest {
    private val base = createTempDirectory("sandbox").toRealPath()
    private val root = base.resolve("p").also { it.createDirectories() }
    private val shell = createShell()

    init {
        root.resolve("a.txt").writeText("a")
        root.resolve("sub").createDirectories()
        root.resolve("sub/b.txt").writeText("b")
        root.resolve(".env").writeText("SECRET=1")
        root.resolve(".git").createDirectories()
        base.resolve("outside.txt").writeText("x")
        Files.createSymbolicLink(root.resolve("escape"), base.resolve("outside.txt"))
        Files.createSymbolicLink(root.resolve("inside"), root.resolve("sub"))
    }

    private val sandbox = Sandbox(shell, root.toString())

    @Test fun `resolves files and folders inside the root`() = runTest {
        assertEquals("$root/a.txt", sandbox.resolve("a.txt"))
        assertEquals("$root/sub", sandbox.resolve("sub"))
        assertEquals(root.toString(), sandbox.resolve(""))
        assertEquals("$root/sub/b.txt", sandbox.resolve("inside/b.txt"))
    }

    @Test fun `refuses escapes, absolute paths, symlinks out and credentials`() = runTest {
        for (path in listOf("../outside.txt", "sub/../../outside.txt", "$base/outside.txt", "~/x", "escape", ".env", ".git/config", "missing.txt")) {
            assertFailsWith<IllegalArgumentException>(path) { sandbox.resolve(path) }
        }
    }

    @Test fun `an absolute path is accepted only when allowed and inside the root`() = runTest {
        val open = Sandbox(shell, root.toString(), allowAbsolute = true)
        assertEquals("$root/a.txt", open.resolve("$root/a.txt"))
        assertFailsWith<IllegalArgumentException> { open.resolve("$base/outside.txt") }
        assertFailsWith<IllegalArgumentException> { open.resolve("$root/.env") }
        assertEquals("$root/new.txt", open.resolveForWrite("$root/new.txt"))
        assertFailsWith<IllegalArgumentException> { open.resolveForWrite("$base/new.txt") }
    }

    @Test fun `resolveForWrite allows a new file but not an escape or a credential`() = runTest {
        assertEquals("$root/sub/new.txt", sandbox.resolveForWrite("sub/new.txt"))
        assertFailsWith<IllegalArgumentException> { sandbox.resolveForWrite("escape") }
        assertFailsWith<IllegalArgumentException> { sandbox.resolveForWrite("../new.txt") }
        assertFailsWith<IllegalArgumentException> { sandbox.resolveForWrite("sub/.env") }
        assertFailsWith<IllegalArgumentException> { sandbox.resolveForWrite("nodir/new.txt") }
    }

    @Test fun `createParents resolves the nearest folder that exists`() = runTest {
        assertEquals("$root/sub/x/y/new.txt", sandbox.resolveForWrite("sub/x/y/new.txt", createParents = true))
        assertEquals("$root/top/new.txt", sandbox.resolveForWrite("top/new.txt", createParents = true))
        assertEquals("$root/sub/x/new.txt", sandbox.resolveForWrite("inside/x/new.txt", createParents = true))
        assertFailsWith<IllegalArgumentException> { sandbox.resolveForWrite("escape/x/new.txt", createParents = true) }
        assertFailsWith<IllegalArgumentException> { sandbox.resolveForWrite("../x/new.txt", createParents = true) }
        assertFailsWith<IllegalArgumentException> { sandbox.resolveForWrite("a/.git/x.txt", createParents = true) }
        val open = Sandbox(shell, root.toString(), allowAbsolute = true)
        assertEquals("$root/n/new.txt", open.resolveForWrite("$root/n/new.txt", createParents = true))
        assertFailsWith<IllegalArgumentException> { open.resolveForWrite("$base/n/new.txt", createParents = true) }
    }

    @Test fun `isSensitive`() {
        assertTrue(Sandbox.isSensitive("x/secrets/y"))
        assertTrue(Sandbox.isSensitive("release.jks"))
        assertTrue(!Sandbox.isSensitive("src/Main.kt"))
    }
}
