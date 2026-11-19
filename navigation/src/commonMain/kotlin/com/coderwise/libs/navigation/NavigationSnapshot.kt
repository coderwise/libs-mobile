package com.coderwise.libs.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The serializable form of a [NavigationState]. [NavKey] is an interface, so its keys are
 * written polymorphically: the `SavedStateConfiguration` used to save and restore must
 * register every key type under `polymorphic(NavKey::class)`.
 */
@Serializable
internal data class NavigationSnapshot(
    val root: List<NavKey>,
    val children: List<ChildBackStack>,
)

@Serializable
internal data class ChildBackStack(
    val rootKey: NavKey,
    val stack: List<NavKey>,
)
