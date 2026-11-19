package com.coderwise.libs.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

class SettingsDataStoreFactoryTest {

    @Serializable
    private data class Prefs(val theme: String = "system")

    private val root = Path(NSTemporaryDirectory(), "settings-" + NSUUID().UUIDString)

    @AfterTest
    fun cleanUp() {
        runCatching { SystemFileSystem.delete(Path(root, "nested/.myApp/settings.json"), mustExist = false) }
    }

    @Test
    fun `settings are written under the directory the app names and it is created if missing`() = runTest {
        val directory = Path(root, "nested/.myApp")
        val store = SettingsDataStoreFactory(directory).create("settings.json", Prefs(), Prefs.serializer())

        store.updateData { it.copy(theme = "dark") }

        assertTrue(SystemFileSystem.exists(Path(directory, "settings.json")))
        assertEquals("dark", store.data.first().theme)
    }
}
