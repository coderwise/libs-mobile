package com.coderwise.libs.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsDataStoreFactoryTest {

    @Serializable
    private data class Prefs(val theme: String = "system")

    private val root = Path(SystemTemporaryDirectory, "settings-${Random.nextLong().toULong()}")

    private fun Path.isFile() = SystemFileSystem.metadataOrNull(this)?.isRegularFile == true

    @AfterTest
    fun cleanUp() {
        deleteRecursively(root)
    }

    private fun deleteRecursively(path: Path) {
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach(::deleteRecursively)
        }
        SystemFileSystem.delete(path, mustExist = false)
    }

    @Test
    fun `settings are written under the directory the app names, which is created if missing`() = runTest {
        val directory = Path(root, "nested/.myApp")
        val store = SettingsDataStoreFactory(directory).create("settings.json", Prefs(), Prefs.serializer())

        store.updateData { it.copy(theme = "dark") }

        assertTrue(Path(directory, "settings.json").isFile())
        assertEquals("dark", store.data.first().theme)
    }

    @Test
    fun `two apps with their own directories keep their own settings`() = runTest {
        val first = SettingsDataStoreFactory(Path(root, "a")).create("settings.json", Prefs(), Prefs.serializer())
        val second = SettingsDataStoreFactory(Path(root, "b")).create("settings.json", Prefs(), Prefs.serializer())

        first.updateData { it.copy(theme = "dark") }

        assertEquals("system", second.data.first().theme)
    }

    @Test
    fun `a kotlinx-io Path names the directory too`() = runTest {
        val directory = Path(root, "io/.myApp")
        val store = SettingsDataStoreFactory(directory).create("settings.json", Prefs(), Prefs.serializer())

        store.updateData { it.copy(theme = "dark") }

        assertTrue(Path(directory, "settings.json").isFile())
    }
}
