package com.coderwise.libs.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File

/** The limits and switches of the rules; the defaults are the Coderwise standard. */
data class ArchitectureConfig(
    /** A `*ViewModel.kt` file stays at or under this many lines. */
    val maxViewModelLines: Int = 400,
    /** A ViewModel's constructor takes at most this many parameters. */
    val maxViewModelDeps: Int = 10,
    /** Top-level directories that hold no platform source sets and no expect/actual. */
    val platformFreeDirs: Set<String> = setOf("core", "feature"),
    /** Feature ViewModels extend `StateViewModel`. */
    val requireStateViewModel: Boolean = true,
    /** Top-level directories whose `ui` package root is restricted. */
    val uiRootDirs: Set<String> = setOf("feature"),
    /** A file directly in a `ui` package ends in one of these (`ChatScreen.kt`); anything else goes in a subpackage. */
    val uiRootSuffixes: Set<String> = setOf("Screen", "Content", "ViewModel", "UiState", "UiEvent", "Previews"),
    /** Where the baseline lives, relative to the project root. */
    val baselineFile: String = "scripts/architecture/baseline.txt",
)

/**
 * Architecture rules for a Coderwise app, run as one test:
 *
 * ```
 * class ArchitectureTest {
 *     @Test fun `architecture rules hold`() = ArchitectureRules.verify()
 * }
 * ```
 *
 * Rules: ViewModel files stay short; ViewModels take few dependencies; feature ViewModels extend
 * `StateViewModel`; the root of a feature's `ui` package holds only screens, content, view models, UI state and
 * events and previews (see [ArchitectureConfig.uiRootSuffixes]), everything else lives in a subpackage such as
 * `components`, `theme` or `controller`; a `*Content` composable that takes `onEvent` takes no other `on*` callback; `core/` and
 * `feature/` hold no platform code.
 *
 * Code that already breaks a rule is listed in the baseline (see [ArchitectureConfig.baselineFile]); a listed
 * entry may shrink but not grow. Set the environment variable `ARCHITECTURE_UPDATE_BASELINE=true` to rewrite
 * the baseline from the current code.
 */
object ArchitectureRules {

    /** Throws [AssertionError] listing every break the baseline does not cover. */
    fun verify(config: ArchitectureConfig = ArchitectureConfig(), projectDir: File = findProjectDir()) {
        check(generateSequence(projectDir.absoluteFile) { it.parentFile }.any { File(it, ".git").exists() }) {
            "Konsist finds the project root through a .git directory and none exists at or above $projectDir: " +
                "run `git init` (a checkout has one already)"
        }
        // An absolute path, so it is right even when the project sits inside a larger repository.
        val scope = Konsist.scopeFromExternalDirectory(projectDir.absolutePath)
        val found = Rules.find(scope, projectDir, config)
        val baselineFile = File(projectDir, config.baselineFile)

        if (System.getenv("ARCHITECTURE_UPDATE_BASELINE") == "true") {
            Baseline.write(baselineFile, found)
            println("Wrote ${found.size} entries to ${config.baselineFile}")
            return
        }

        val verdict = verdict(found, Baseline.read(baselineFile), config)
        verdict.fixed.forEach { println("note: fixed, remove from the baseline: $it") }
        if (verdict.failures.isNotEmpty()) {
            throw AssertionError(
                verdict.failures.joinToString("\n", prefix = "Architecture rules broken:\n") { "  $it" } +
                    "\nSplit large view models into controllers, raise UiEvents from content composables instead of " +
                    "taking callbacks, and keep platform code in libs/ or app/.",
            )
        }
    }

    /** The directory above the working directory that holds `project.yaml` (Amper) or `settings.gradle.kts`. */
    fun findProjectDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "project.yaml").exists() || File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("No project.yaml or settings.gradle.kts above ${File("").absolutePath}")
    }
}
