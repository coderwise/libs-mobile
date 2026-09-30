package com.coderwise.libs.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsDataStoreFactoryTest {

    @Serializable
    private data class Prefs(val theme: String = "system")

    private val root: File = Files.createTempDirectory("settings").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `settings are written under the directory the app names, which is created if missing`() = runTest {
        val directory = File(root, "nested/.myApp")
        val store = SettingsDataStoreFactory(directory).create("settings.json", Prefs(), Prefs.serializer())

        store.updateData { it.copy(theme = "dark") }

        assertTrue(File(directory, "settings.json").isFile)
        assertEquals("dark", store.data.first().theme)
    }

    @Test
    fun `two apps with their own directories keep their own settings`() = runTest {
        val first = SettingsDataStoreFactory(File(root, "a")).create("settings.json", Prefs(), Prefs.serializer())
        val second = SettingsDataStoreFactory(File(root, "b")).create("settings.json", Prefs(), Prefs.serializer())

        first.updateData { it.copy(theme = "dark") }

        assertEquals("system", second.data.first().theme)
    }
}
