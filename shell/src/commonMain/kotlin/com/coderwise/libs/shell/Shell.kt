package com.coderwise.libs.shell

data class ShellEntry(val name: String, val isDirectory: Boolean)

data class ShellResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Host access (processes and files). Files use kotlinx-io on every target; only starting processes and the home
 * directory are `expect`/`actual`, and targets that cannot spawn processes return no result.
 */
interface Shell {
    /** Runs [command] in [dir]; null if it could not start or timed out. */
    suspend fun run(dir: String?, command: List<String>, timeoutSeconds: Long = 20): ShellResult?
    suspend fun readText(path: String): String?
    suspend fun writeText(path: String, text: String)
    suspend fun exists(path: String): Boolean
    /** Names of the subdirectories of [path]. */
    suspend fun listDirs(path: String): List<String>
    /** Files and subdirectories of [path] (empty if it is not a directory). */
    suspend fun listEntries(path: String): List<ShellEntry>
    /** The real path with `.` and symlinks resolved; null if it does not exist. */
    suspend fun canonicalPath(path: String): String?
    fun homeDir(): String
}

fun createShell(): Shell = DefaultShell
