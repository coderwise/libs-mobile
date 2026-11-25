package com.coderwise.libs.architecture

import java.io.File

/** A rule and what it was broken in: a file, or `file:Declaration`. */
internal data class Violation(val rule: String, val target: String)

/**
 * Code that already breaks a rule, grandfathered, one entry per line:
 * `<rule> <file[:declaration]> <allowance>`. An entry may shrink but not grow: a ViewModel may get shorter
 * but not longer, a composable may keep the callbacks listed and no more. Remove an entry once the code
 * is fixed, and never add one to get a change through.
 */
internal class Baseline(val entries: Map<Violation, String>) {

    fun allows(violation: Violation, value: String): Boolean {
        val allowance = entries[violation] ?: return false
        return when (violation.rule) {
            Rules.CONTENT_LAMBDAS -> allowance.split(",").toSet().containsAll(value.split(",").toSet())
            Rules.PLATFORM_CODE, Rules.VIEWMODEL_BASE -> true
            else -> (value.toIntOrNull() ?: Int.MAX_VALUE) <= (allowance.toIntOrNull() ?: -1)
        }
    }

    companion object {
        fun read(file: File): Baseline {
            if (!file.exists()) return Baseline(emptyMap())
            val entries = file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { line ->
                    val (rule, target, allowance) = line.split(Regex("\\s+"), limit = 3)
                    Violation(rule, target) to allowance
                }
            return Baseline(entries)
        }

        fun write(file: File, found: Map<Violation, String>) {
            val header = "# Grandfathered breaks of the architecture rules, one per line:\n" +
                "#   <rule> <file[:declaration]> <allowance>\n" +
                "# Remove an entry once the code is fixed. Don't add new ones to get a change through.\n"
            val body = found.entries
                .sortedWith(compareBy({ it.key.rule }, { it.key.target }))
                .joinToString("") { "${it.key.rule} ${it.key.target} ${it.value}\n" }
            file.parentFile?.mkdirs()
            file.writeText(header + body)
        }
    }
}

/** What [verify] found: the breaks the baseline does not cover, and baseline entries whose code is now fixed. */
internal class Verdict(val failures: List<String>, val fixed: List<String>)

internal fun verdict(found: Map<Violation, String>, baseline: Baseline, config: ArchitectureConfig): Verdict {
    val failures = found.entries.sortedWith(compareBy({ it.key.rule }, { it.key.target })).mapNotNull { (violation, value) ->
        if (baseline.allows(violation, value)) return@mapNotNull null
        val what = when (violation.rule) {
            Rules.VIEWMODEL_LINES -> "$value lines, limit ${config.maxViewModelLines}"
            Rules.VIEWMODEL_DEPS -> "$value constructor parameters, limit ${config.maxViewModelDeps}"
            Rules.CONTENT_LAMBDAS -> "takes onEvent and also $value"
            Rules.PLATFORM_CODE -> "platform code ($value) in a ${config.platformFreeDirs.joinToString("/")} module; " +
                "move it to libs/ or app/"
            Rules.VIEWMODEL_BASE -> "a feature ViewModel must extend StateViewModel"
            else -> value
        }
        val allowance = baseline.entries[violation]?.let { " (baseline allows $it)" }.orEmpty()
        "${violation.rule}: ${violation.target}: $what$allowance"
    }
    val fixed = baseline.entries.keys.filter { it !in found }.map { "${it.rule} ${it.target}" }.sorted()
    return Verdict(failures, fixed)
}
