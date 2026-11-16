package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coderwise.libs.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class DemoSettings(val opens: Int = 0, val note: String = "")

// A DataStore must be the only one over its file in the process, and this page is built again
// every time it is opened. An app holds its store in DI; the sample has no graph, so it keeps the
// one it made here.
private var sharedStore: SettingsDataStore<DemoSettings>? = null

@Composable
private fun rememberDemoStore(): SettingsDataStore<DemoSettings> {
    val factory = rememberSettingsDataStoreFactory()
    return remember(factory) {
        sharedStore ?: factory
            .create("sample-settings.json", DemoSettings(), DemoSettings.serializer())
            .also { sharedStore = it }
    }
}

@Composable
internal fun SettingsExample() {
    val store = rememberDemoStore()
    val stored by store.data.collectAsState(initial = DemoSettings())
    val scope = rememberCoroutineScope()
    // The field edits a draft rather than the stored value: a write is asynchronous, so binding
    // the field to it would drop keystrokes typed before the last one came back.
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(store) {
        draft = store.data.first().note
        store.updateData { it.copy(opens = it.opens + 1) }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DemoSection("Stored") {
            ReadoutRow("opens", stored.opens.toString())
            ReadoutRow("note", stored.note.ifEmpty { "(empty)" })
        }
        DemoSection("Change it") {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { store.updateData { it.copy(note = draft) } } }) {
                    Text("Save")
                }
                OutlinedButton(onClick = {
                    draft = ""
                    scope.launch { store.updateData { DemoSettings() } }
                }) { Text("Reset") }
            }
        }
        PlatformNote(
            "\"opens\" goes up each time this page is shown, and both values are read back from " +
                "disk when the app next starts — close it and reopen it to see them survive. " +
                "Android, iOS and desktop keep a file under a DataStore, with `updateData` " +
                "applying a transform atomically; the browser keeps the same JSON in " +
                "localStorage. Unknown keys are ignored on read, so dropping a field from a " +
                "settings class does not reset what a user has already saved."
        )
    }
}
