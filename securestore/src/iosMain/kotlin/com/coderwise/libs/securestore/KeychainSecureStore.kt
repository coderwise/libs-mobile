@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coderwise.libs.securestore

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import kotlinx.cinterop.COpaquePointer

/** Values live in the iOS Keychain as generic passwords, readable after first unlock, this device only. */
internal class KeychainSecureStore(private val service: String) : SecureStore {

    override fun get(key: String): String? = memScoped {
        val query = dictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service.retained(),
            kSecAttrAccount to key.retained(),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun put(key: String, value: String?) {
        delete(key)
        if (value == null) return
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.convert()) }
        val query = dictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service.retained(),
            kSecAttrAccount to key.retained(),
            kSecValueData to CFBridgingRetain(data),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        SecItemAdd(query, null)
        CFRelease(query)
    }

    private fun delete(key: String) {
        val query = dictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service.retained(),
            kSecAttrAccount to key.retained(),
        )
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun String.retained(): COpaquePointer? = CFBridgingRetain(this)

    private fun dictionary(vararg entries: Pair<COpaquePointer?, COpaquePointer?>): CFDictionaryRef? = memScoped {
        CFDictionaryCreate(
            kCFAllocatorDefault,
            allocArrayOf(*entries.map { it.first }.toTypedArray()),
            allocArrayOf(*entries.map { it.second }.toTypedArray()),
            entries.size.convert(),
            null,
            null,
        )
    }

}
