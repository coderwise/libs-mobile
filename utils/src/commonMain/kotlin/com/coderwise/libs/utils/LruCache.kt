package com.coderwise.libs.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory least-recently-used cache holding up to [maxSize] entries, keyed by [K].
 *
 * [peek] is a non-suspending best-effort read for fast paths (returns null rather than blocking if
 * the lock is held); [get] and [put] take the lock and maintain recency order, evicting the
 * least-recently-used entry once the cache is over capacity.
 */
class LruCache<K, V>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()
    private val mutex = Mutex()

    fun peek(key: K): V? =
        if (mutex.tryLock()) try { map[key] } finally { mutex.unlock() } else null

    suspend fun get(key: K): V? = mutex.withLock {
        val value = map.remove(key) ?: return@withLock null
        map[key] = value
        value
    }

    suspend fun put(key: K, value: V) = mutex.withLock {
        map.remove(key)
        map[key] = value
        while (map.size > maxSize) {
            val oldest = map.keys.iterator().next()
            map.remove(oldest)
        }
    }
}
