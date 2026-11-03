package com.coderwise.libs.mapview.cache

import com.coderwise.libs.mapview.TileKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Tiles kept as files under [directory], one per tile at `source/z/x/y.tile`, up to [maxBytes] in
 * all, the least recently used going first.
 *
 * Taking the [fileSystem] rather than reaching for one is what keeps this in common code: on
 * Android, iOS and the desktop it is `FileSystem.SYSTEM` over a directory the app chooses — the
 * app's cache directory, so the system may reclaim it — and the browser has no file system to give
 * it, so a web build uses a [MemoryTileStore] instead.
 *
 * The first use reads what is already on disk, so a cache survives the process. Recency is kept in
 * memory: files are only ever written, never touched, so after a restart the order they were
 * written in stands in for the order they were used in.
 *
 * A tile is written beside its place and moved there, so one that was being written when the
 * process died is never mistaken for a whole one. The disk work is blocking and runs on
 * [Dispatchers.Default], off whichever thread asked.
 */
class FileTileStore(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val maxBytes: Long = 200L * 1024 * 1024
) : TileStore {

    private val lock = Mutex()
    private val index = LinkedHashMap<Path, Long>() // file to its size, least recently used first
    private var total = 0L
    private var scanned = false

    override suspend fun get(source: String, key: TileKey): ByteArray? = locked {
        val file = file(source, key)
        val size = index.remove(file) ?: return@locked null
        val bytes = try {
            fileSystem.read(file) { readByteArray() }
        } catch (_: IOException) {
            // Taken away behind our back — a system that reclaims a cache directory does it.
            total -= size
            return@locked null
        }
        index[file] = size
        bytes
    }

    override suspend fun put(source: String, key: TileKey, bytes: ByteArray) {
        locked {
            if (bytes.size > maxBytes) return@locked
            val file = file(source, key)
            val folder = file.parent!!
            val partial = folder / "${file.name}$PARTIAL"
            try {
                fileSystem.createDirectories(folder)
                fileSystem.write(partial) { write(bytes) }
                fileSystem.atomicMove(partial, file)
            } catch (e: IOException) {
                fileSystem.delete(partial, mustExist = false)
                throw e
            }
            total += bytes.size - (index.remove(file) ?: 0L)
            index[file] = bytes.size.toLong()
            evict()
        }
    }

    override suspend fun remove(source: String, key: TileKey) {
        locked {
            val file = file(source, key)
            index.remove(file)?.let { total -= it }
            fileSystem.delete(file, mustExist = false)
        }
    }

    override suspend fun clear(source: String?) {
        locked {
            if (source == null) {
                fileSystem.deleteRecursively(directory, mustExist = false)
                index.clear()
                total = 0
            } else {
                val folder = directory / segment(source)
                fileSystem.deleteRecursively(folder, mustExist = false)
                val all = index.entries.iterator()
                while (all.hasNext()) {
                    val entry = all.next()
                    // source/z/x/y.tile: three folders up from the file is the source's.
                    if (entry.key.parent?.parent?.parent == folder) {
                        total -= entry.value
                        all.remove()
                    }
                }
            }
        }
    }

    /** How many bytes of tiles are held. */
    suspend fun size(): Long = locked { total }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.Default) {
        lock.withLock {
            scan()
            block()
        }
    }

    private fun file(source: String, key: TileKey): Path =
        directory / segment(source) / key.z.toString() / key.x.toString() / "${key.y}$TILE"

    private fun evict() {
        val oldest = index.entries.iterator()
        while (total > maxBytes && oldest.hasNext()) {
            // Read before it is removed: a map entry is not valid once its iterator has taken it out.
            val entry = oldest.next()
            val file = entry.key
            val size = entry.value
            oldest.remove()
            total -= size
            fileSystem.delete(file, mustExist = false)
        }
    }

    /** Reads what an earlier run left, once, oldest first. */
    private fun scan() {
        if (scanned) return
        scanned = true
        if (!fileSystem.exists(directory)) return
        val found = mutableListOf<Triple<Path, Long, Long>>()
        for (path in fileSystem.listRecursively(directory)) {
            val meta = fileSystem.metadataOrNull(path)
            if (meta == null || !meta.isRegularFile) continue
            when {
                path.name.endsWith(PARTIAL) -> fileSystem.delete(path, mustExist = false)
                path.name.endsWith(TILE) -> found += Triple(path, meta.size ?: 0L, meta.lastModifiedAtMillis ?: 0L)
            }
        }
        found.sortedBy { it.third }.forEach { (path, size, _) ->
            index[path] = size
            total += size
        }
        evict() // a smaller limit than the last run had
    }
}

/**
 * A source as one path segment: letters, digits, `.`, `_` and `-` as they are, and every other
 * byte as `%` and two hex digits. Injective, so two sources never share a folder, and nothing in
 * it can be a path separator or climb out of the cache.
 */
internal fun segment(source: String): String {
    require(source.isNotEmpty()) { "a source needs a name" }
    val out = StringBuilder()
    for (byte in source.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val plain = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
            c == '.'.code || c == '_'.code || c == '-'.code
        if (plain) out.append(c.toChar()) else out.append('%').append(HEX[c shr 4]).append(HEX[c and 15])
    }
    // "." and ".." are not names for a folder.
    return if (out.all { it == '.' }) out.toString().replace(".", "%2E") else out.toString()
}

private const val HEX = "0123456789ABCDEF"
private const val TILE = ".tile"
private const val PARTIAL = ".partial"
