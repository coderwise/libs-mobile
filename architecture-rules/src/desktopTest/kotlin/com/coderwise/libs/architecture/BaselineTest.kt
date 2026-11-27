package com.coderwise.libs.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaselineTest {

    private val config = ArchitectureConfig()
    private fun baseline(vararg entries: Pair<Violation, String>) = Baseline(mapOf(*entries))

    @Test
    fun `an unlisted break fails`() {
        val found = mapOf(Violation(Rules.VIEWMODEL_LINES, "a/HomeViewModel.kt") to "450")
        val verdict = verdict(found, baseline(), config)
        assertEquals(listOf("viewmodel-lines: a/HomeViewModel.kt: 450 lines, limit 400"), verdict.failures)
    }

    @Test
    fun `a listed ViewModel may shrink but not grow`() {
        val key = Violation(Rules.VIEWMODEL_LINES, "a/HomeViewModel.kt")
        val listed = baseline(key to "450")
        assertTrue(listed.allows(key, "450"))
        assertTrue(listed.allows(key, "420"))
        assertFalse(listed.allows(key, "451"))
    }

    @Test
    fun `a listed composable keeps only the callbacks listed`() {
        val key = Violation(Rules.CONTENT_LAMBDAS, "a/HomeScreen.kt:HomeScreenContent")
        val listed = baseline(key to "onBack,onRefresh")
        assertTrue(listed.allows(key, "onBack"))
        assertTrue(listed.allows(key, "onBack,onRefresh"))
        assertFalse(listed.allows(key, "onBack,onShare"))
    }

    @Test
    fun `platform code and base class breaks are allowed once listed`() {
        val platform = Violation(Rules.PLATFORM_CODE, "feature/x/src@android/A.kt")
        val base = Violation(Rules.VIEWMODEL_BASE, "feature/x/Settings.kt:SettingsViewModel")
        val listed = baseline(platform to "any", base to "any")
        assertTrue(listed.allows(platform, "src@android"))
        assertTrue(listed.allows(base, "not-StateViewModel"))
    }

    @Test
    fun `an entry whose code is fixed is reported`() {
        val key = Violation(Rules.VIEWMODEL_DEPS, "a/HomeViewModel.kt:HomeViewModel")
        val verdict = verdict(emptyMap(), baseline(key to "12"), config)
        assertEquals(emptyList(), verdict.failures)
        assertEquals(listOf("viewmodel-deps a/HomeViewModel.kt:HomeViewModel"), verdict.fixed)
    }

    @Test
    fun `the baseline file round-trips`() {
        val file = File.createTempFile("baseline", ".txt")
        val found = mapOf(
            Violation(Rules.VIEWMODEL_LINES, "b.kt") to "450",
            Violation(Rules.CONTENT_LAMBDAS, "a.kt:AContent") to "onBack",
        )
        Baseline.write(file, found)
        val read = Baseline.read(file)
        assertEquals(found, read.entries)
        file.delete()
    }

    @Test
    fun `a missing baseline file is an empty baseline`() {
        assertEquals(emptyMap(), Baseline.read(File("/nonexistent/baseline.txt")).entries)
    }

    @Test
    fun `a stray file in the ui root fails and can be listed`() {
        val key = Violation(Rules.UI_ROOT, "feature/chat/src/x/ui/Helpers.kt")
        val verdict = verdict(mapOf(key to "Helpers.kt"), baseline(), config)
        assertTrue(verdict.failures.single().startsWith("ui-root: feature/chat/src/x/ui/Helpers.kt: Helpers.kt sits in the root of a ui package"))
        assertTrue(baseline(key to "Helpers.kt").allows(key, "Helpers.kt"))
    }
}
