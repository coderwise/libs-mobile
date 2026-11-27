package com.coderwise.libs.shell


/**
 * Keeps file access by a model inside one directory, [root], and away from credentials. Every resolved path is the
 * real path (symlinks followed), so a link cannot lead out. Refusals throw [IllegalArgumentException] with a message
 * meant for the model.
 *
 * [allowAbsolute] accepts an absolute path as long as it resolves inside [root]; otherwise only relative paths pass.
 */
class Sandbox(private val shell: Shell, val root: String, private val allowAbsolute: Boolean = false) {

    /** The real path of [relative] inside the root; it must exist and must not look like a credential. */
    suspend fun resolve(relative: String): String {
        val rel = relative.trim().ifEmpty { "." }
        val absolute = rel.startsWith("/")
        require(!rel.startsWith("~") && (allowAbsolute || !absolute)) { "Path must be relative to the project" }
        require(rel.split('/').none { it == ".." }) { "Path must stay inside the project" }
        require(absolute || !isSensitive(rel)) { "Access to $rel is not allowed" }
        val base = shell.canonicalPath(root) ?: throw IllegalArgumentException("Unknown directory: $root")
        val target = shell.canonicalPath(if (absolute) rel else "$base/$rel")
            ?: throw IllegalArgumentException("No such file or directory: $rel")
        require(target == base || target.startsWith("$base/")) { "Path must stay inside the project" }
        require(!isSensitive(target.removePrefix(base))) { "Access to $rel is not allowed" }
        return target
    }

    /**
     * Like [resolve] but for a file that may not exist yet: its parent folder must resolve inside the root, or with
     * [createParents] the nearest folder that exists must, and the missing ones below it are the caller's to create.
     */
    suspend fun resolveForWrite(relative: String, createParents: Boolean = false): String {
        val rel = relative.trim().trimEnd('/')
        require(rel.isNotEmpty()) { "path is required" }
        val name = rel.substringAfterLast('/')
        require(name != "." && name.isNotEmpty()) { "Not a file path: $relative" }
        require(!isSensitive(name)) { "Access to $rel is not allowed" }
        val parentRel = rel.substringBeforeLast('/', if (rel.startsWith("/")) "/" else ".")
        val parent = if (createParents) resolveNearest(parentRel) else resolve(parentRel)
        val target = if (parent.endsWith("/")) "$parent$name" else "$parent/$name"
        // An existing entry (e.g. a symlink) must itself resolve inside the root.
        if (shell.exists(target)) resolve(rel)
        return target
    }

    /** The real path of [folder], where the part that does not exist yet is appended to the nearest one that does. */
    private suspend fun resolveNearest(folder: String): String {
        val parts = folder.split('/')
        val base = shell.canonicalPath(root) ?: throw IllegalArgumentException("Unknown directory: $root")
        for (keep in parts.size downTo 1) {
            val head = parts.take(keep).joinToString("/").ifEmpty { "/" }
            // A relative path is the root's, not the process's.
            if (!shell.exists(if (head.startsWith("/")) head else "$base/$head")) continue
            val existing = resolve(head)
            val rest = parts.drop(keep).filter { it.isNotEmpty() && it != "." }
            require(rest.none { isSensitive(it) }) { "Access to $folder is not allowed" }
            return (listOf(existing) + rest).joinToString("/")
        }
        // Nothing of a relative folder exists yet: it starts at the root.
        val rest = parts.filter { it.isNotEmpty() && it != "." }
        require(rest.none { isSensitive(it) }) { "Access to $folder is not allowed" }
        return (listOf(resolve(".")) + rest).joinToString("/")
    }

    companion object {
        private val sensitiveDirs = setOf(".git", "secrets", ".ssh")
        private val sensitiveNames = setOf("local.properties", "keystore.properties", "google-services.json", "GoogleService-Info.plist")
        private val sensitiveSuffixes = listOf(".jks", ".keystore", ".pem", ".p12")

        /** Directories that are only noise for a model. */
        val skippedDirs = setOf(".git", "build", ".gradle", ".kotlin", ".idea", "node_modules", "kotlin-js-store", ".DS_Store")

        /** Whether [path] is, or is inside, something that holds credentials. */
        fun isSensitive(path: String): Boolean {
            val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
            val name = parts.lastOrNull() ?: return false
            return parts.any { it in sensitiveDirs } ||
                name in sensitiveNames ||
                name.startsWith(".env") ||
                sensitiveSuffixes.any { name.endsWith(it) }
        }
    }
}
