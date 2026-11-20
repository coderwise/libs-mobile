package com.coderwise.libs.navigation.ui

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SceneStrategyTest {

    private fun entry(key: String, metadata: Map<String, Any> = emptyMap()) =
        NavEntry(key, metadata = metadata) {}

    // SceneStrategyScope can't be built outside Navigation 3, so the tests call what
    // calculateScene delegates to.
    private fun <T : Any> BottomSheetSceneStrategy<T>.sceneFor(entries: List<NavEntry<T>>) =
        sceneFor(entries, onBack = {})

    @Test
    fun `an unmarked top entry gets no sheet`() {
        val scene = BottomSheetSceneStrategy<String>().sceneFor(listOf(entry("a"), entry("b")))
        assertNull(scene)
    }

    @Test
    fun `a marked top entry is a sheet over the entries beneath it`() {
        val beneath = entry("a")
        val sheet = entry("b", BottomSheetSceneStrategy.bottomSheet())
        val scene = BottomSheetSceneStrategy<String>().sceneFor(listOf(beneath, sheet))

        assertIs<OverlayScene<String>>(scene)
        assertEquals(listOf(sheet), scene.entries)
        assertEquals(listOf(beneath), scene.previousEntries)
        assertEquals(listOf(beneath), scene.overlaidEntries)
    }

    @Test
    fun `a marked entry beneath the top one gets no sheet`() {
        val scene = BottomSheetSceneStrategy<String>().sceneFor(
            listOf(entry("a", BottomSheetSceneStrategy.bottomSheet()), entry("b"))
        )
        assertNull(scene)
    }

    @Test
    fun `the sheet dismisses through onBack by default`() {
        var backs = 0
        val scene = BottomSheetSceneStrategy<String>().sceneFor(
            listOf(entry("a", BottomSheetSceneStrategy.bottomSheet())),
            onBack = { backs++ },
        )
        assertIs<BottomSheetScene<String>>(scene)
        scene.request.onDismiss()
        assertEquals(1, backs)
    }

    @Test
    fun `an entry's own onDismiss replaces onBack`() {
        var backs = 0
        var dismissals = 0
        val scene = BottomSheetSceneStrategy<String>().sceneFor(
            listOf(entry("a", BottomSheetSceneStrategy.bottomSheet(onDismiss = { dismissals++ }))),
            onBack = { backs++ },
        )
        assertIs<BottomSheetScene<String>>(scene)
        scene.request.onDismiss()
        assertEquals(0, backs)
        assertEquals(1, dismissals)
    }

    @Test
    fun `app metadata added to a sheet reaches the container`() {
        val metadata = BottomSheetSceneStrategy.bottomSheet() + ("title" to "Storage")
        val scene = BottomSheetSceneStrategy<String>().sceneFor(listOf(entry("a", metadata)))
        assertIs<BottomSheetScene<String>>(scene)
        assertEquals("Storage", scene.request.metadata["title"])
    }

    @Test
    fun `the same back stack gives an equal sheet scene`() {
        val entries = listOf(entry("a"), entry("b", BottomSheetSceneStrategy.bottomSheet()))
        val strategy = BottomSheetSceneStrategy<String>()
        assertEquals(strategy.sceneFor(entries), strategy.sceneFor(entries))
    }

    @Test
    fun `an overlay entry is drawn over the entries beneath it`() {
        val beneath = entry("a")
        val top = entry("b", OverlaySceneStrategy.overlay())
        val scene = OverlaySceneStrategy<String>().sceneFor(listOf(beneath, top))

        assertIs<OverlayScene<String>>(scene)
        assertEquals(listOf(top), scene.entries)
        assertEquals(listOf(beneath), scene.overlaidEntries)
    }

    @Test
    fun `an unmarked top entry is not an overlay`() {
        assertNull(OverlaySceneStrategy<String>().sceneFor(listOf(entry("a"))))
    }
}
