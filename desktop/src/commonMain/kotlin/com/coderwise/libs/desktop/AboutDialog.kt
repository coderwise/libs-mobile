package com.coderwise.libs.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Text shown in the macOS "About" window. Pass already-localized strings. */
class AboutInfo(
    val description: String = "",
    val copyright: String = "",
    /** Formats the version line, e.g. `"Version $it"`. */
    val versionLabel: (version: String) -> String = { "Version $it" },
)

@Composable
internal fun AboutDialogContent(appName: String, appVersion: String, about: AboutInfo, icon: Painter?) {
    Column(
        modifier = Modifier.fillMaxSize().widthIn(min = 280.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) Icon(painter = icon, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            about.versionLabel(appVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (about.description.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                about.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (about.copyright.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                about.copyright,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The About window's content: the app's theme and surface around [DesktopAppConfig.aboutContent] or the default content. */
@Composable
internal fun AboutWindowContent(config: DesktopAppConfig, icon: Painter?, onClose: () -> Unit) {
    config.theme(isSystemInDarkTheme()) {
        Surface(Modifier.fillMaxSize()) {
            val custom = config.aboutContent
            if (custom != null) {
                custom(icon, onClose)
            } else {
                AboutDialogContent(config.appName, config.appVersion, config.about ?: AboutInfo(), icon)
            }
        }
    }
}
