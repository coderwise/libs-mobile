package com.coderwise.libs.securestore

/** Small secret storage (Keychain on iOS, Keystore-encrypted prefs on Android, memory on desktop and web). */
interface SecureStore {
    fun get(key: String): String?

    /** Stores [value], or removes [key] when [value] is null. */
    fun put(key: String, value: String?)
}

/** Non-persistent store, used on platforms without secure storage and handy in tests. */
class MemorySecureStore : SecureStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
