package com.coderwise.libs.architecture

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.modifier.KoModifierProvider
import java.io.File

internal object Rules {
    const val VIEWMODEL_LINES = "viewmodel-lines"
    const val VIEWMODEL_DEPS = "viewmodel-deps"
    const val VIEWMODEL_BASE = "viewmodel-base"
    private val STATE_VIEWMODEL_BASES = setOf("StateViewModel", "ModelViewModel")
    const val CONTENT_LAMBDAS = "content-lambdas"
    const val PLATFORM_CODE = "platform-code"
    const val UI_ROOT = "ui-root"

    private val skippedDirs = setOf("build", ".git", ".kotlin", "node_modules", "kotlin-js-store")

    /** Production sources only: not tests (`test/`, `commonTest/`, `test@jvm/`, ...), generated code or build output. */
    private fun isProduction(relativePath: String): Boolean =
        relativePath.split('/').none {
            it in skippedDirs || it == "test" || it.endsWith("Test") || it.startsWith("test@")
        }

    /** The platform source set a path is in (`androidMain`, `src@ios`), or null for common code. */
    private fun platformSourceSet(relativePath: String): String? =
        relativePath.split('/').firstOrNull { it.startsWith("src@") || (it.endsWith("Main") && it != "commonMain") }

    fun find(scope: KoScope, root: File, config: ArchitectureConfig): Map<Violation, String> {
        val found = mutableMapOf<Violation, String>()
        val prefix = root.absolutePath.trimEnd('/') + "/"
        fun relative(path: String): String = path.removePrefix(prefix)
        fun production(path: String): Boolean = isProduction(relative(path))
        fun inPlatformFree(path: String): Boolean =
            relative(path).substringBefore('/') in config.platformFreeDirs

        // platform-code: no platform source sets and no expect/actual in core/ or feature/.
        scope.files.filter { production(it.path) && inPlatformFree(it.path) }.forEach { file ->
            platformSourceSet(relative(file.path))?.let { found[Violation(PLATFORM_CODE, relative(file.path))] = it }
        }
        scope.declarations()
            .filter { it is KoModifierProvider && it is KoContainingFileProvider }
            .filter { production((it as KoContainingFileProvider).containingFile.path) }
            .filter { inPlatformFree((it as KoContainingFileProvider).containingFile.path) }
            .filter {
                val modifiers = it as KoModifierProvider
                modifiers.hasModifier(KoModifier.EXPECT) || modifiers.hasModifier(KoModifier.ACTUAL)
            }
            .forEach {
                val path = relative((it as KoContainingFileProvider).containingFile.path)
                found.putIfAbsent(Violation(PLATFORM_CODE, path), "expect/actual")
            }

        // ui-root: only screens, content, view models, state and events sit directly in `ui`.
        scope.files
            .filter { production(it.path) && relative(it.path).substringBefore('/') in config.uiRootDirs }
            .filter { it.path.substringBeforeLast('/').substringAfterLast('/') == "ui" }
            .filter { file -> config.uiRootSuffixes.none { file.name.endsWith(it) } }
            .forEach { found[Violation(UI_ROOT, relative(it.path))] = it.nameWithExtension }

        // viewmodel-lines: a ViewModel file stays short.
        scope.files
            .filter { production(it.path) && it.nameWithExtension.endsWith("ViewModel.kt") }
            .forEach { file ->
                val lines = file.text.lines().size
                if (lines > config.maxViewModelLines) found[Violation(VIEWMODEL_LINES, relative(file.path))] = lines.toString()
            }

        // viewmodel-deps and viewmodel-base.
        scope.classes()
            .filter { production(it.containingFile.path) && it.name.endsWith("ViewModel") }
            .forEach { viewModel ->
                val target = "${relative(viewModel.containingFile.path)}:${viewModel.name}"
                val deps = viewModel.primaryConstructor?.parameters?.size ?: 0
                if (deps > config.maxViewModelDeps) found[Violation(VIEWMODEL_DEPS, target)] = deps.toString()
                // A parent's name keeps its type arguments: `StateViewModel<HomeUiState, HomeUiEvent>`.
                if (config.requireStateViewModel &&
                    relative(viewModel.containingFile.path).substringBefore('/') == "feature" &&
                    viewModel.parents().none { it.name.substringBefore('<') in STATE_VIEWMODEL_BASES }
                ) {
                    found[Violation(VIEWMODEL_BASE, target)] = "not-StateViewModel"
                }
            }

        // content-lambdas: a *Content composable that takes onEvent takes no other on* callback.
        scope.functions()
            .filter { production(it.containingFile.path) && it.hasAnnotationWithName("Composable") }
            .filter { it.name.endsWith("Content") && it.hasParameterWithName("onEvent") }
            .forEach { function ->
                val extra = function.parameters.map { it.name }
                    .filter { it != "onEvent" && Regex("on[A-Z].*").matches(it) }
                    .sorted()
                if (extra.isNotEmpty()) {
                    found[Violation(CONTENT_LAMBDAS, "${relative(function.containingFile.path)}:${function.name}")] =
                        extra.joinToString(",")
                }
            }
        return found
    }
}
