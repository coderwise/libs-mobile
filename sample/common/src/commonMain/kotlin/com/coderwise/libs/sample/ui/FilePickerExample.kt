package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.filepicker.rememberTextFilePicker

/** How much of the file to show — the picker reads all of it; the demo only has to prove it did. */
private const val PREVIEW_CHARS = 2000

@Composable
internal fun TextFilePickerExample() {
    var status by remember { mutableStateOf("Nothing picked yet.") }
    var contents by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberTextFilePicker(
        extensions = listOf("txt", "json", "csv", "md"),
        title = "Pick a text file"
    ) { text ->
        contents = text
        status = when {
            text == null -> "Cancelled, or the file could not be read as text."
            text.isEmpty() -> "Read an empty file."
            else -> "Read ${text.length} characters over ${text.lineSequence().count()} lines."
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = pickFile) { Text("Pick a text file") }
        ReadoutRow("Result", status)
        contents?.takeIf { it.isNotEmpty() }?.let { text ->
            DemoSection("Contents") {
                OutputBox(text.take(PREVIEW_CHARS) + if (text.length > PREVIEW_CHARS) "\n…" else "")
            }
        }
        PlatformNote(
            "The extension filter applies where a platform can filter by extension (desktop, " +
                "web). Android's document picker cannot: a file arrives with whatever type the " +
                "app that wrote it declared, so filtering there hides the very file the user " +
                "came for. The title is the desktop dialog's; the mobile pickers carry their own."
        )
    }
}
