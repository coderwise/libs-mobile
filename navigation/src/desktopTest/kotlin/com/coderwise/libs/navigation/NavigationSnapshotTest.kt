package com.coderwise.libs.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationSnapshotTest {

    private val configuration = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(Home::class, Home.serializer())
                subclass(Profile::class, Profile.serializer())
                subclass(Feed::class, Feed.serializer())
                subclass(Detail::class, Detail.serializer())
            }
        }
    }

    @Test
    fun `a snapshot survives a SavedState round trip`() {
        val state = NavigationState().apply { onStart(Home) }
        state.getBackStack(Home, Feed)
        state.navigate(Detail("1"))
        state.switchRoot(Profile)
        state.navigate(Detail("2"))
        val snapshot = state.snapshot()!!

        val saved = encodeToSavedState(NavigationSnapshot.serializer(), snapshot, configuration)
        val decoded = decodeFromSavedState(NavigationSnapshot.serializer(), saved, configuration)

        assertEquals(snapshot, decoded)
    }
}
