package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.utils.rememberShareTextLauncher

@Composable
internal fun ShareTextExample() {
    val shareLauncher = rememberShareTextLauncher()
    var text by remember { mutableStateOf("Shared from the Coderwise libraries sample.") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Text to share") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { shareLauncher.share(text, "Coderwise libraries sample") },
            enabled = text.isNotBlank()
        ) { Text("Share") }
        PlatformNote(
            "The launcher takes its hosting context from the composition rather than a global, " +
                "and carries the title the Android chooser shows. Android and iOS open the " +
                "share sheet; desktop and web have no such concept, so the text goes to the " +
                "clipboard — \"shared\" there means \"ready to paste\"."
        )
    }
}
