package com.coderwise.libs.utils

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView

@Composable
actual fun PlatformColors(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // enableEdgeToEdge instead of the window.statusBarColor /
            // navigationBarColor setters, which are deprecated (and no-ops)
            // from API 35 where edge-to-edge is enforced.
            val transparent = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ) { darkTheme }
            (view.context as ComponentActivity).enableEdgeToEdge(
                statusBarStyle = transparent,
                navigationBarStyle = transparent,
            )
        }
    }
}
