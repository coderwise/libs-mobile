package com.coderwise.libs.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * What `sqldelight { }` would generate from a `.sq` file, written by hand so the sample needs
 * no Gradle plugin: the driver factory only ever sees a [SqlSchema], and it is the factory
 * that is being shown here.
 */
private object NotesSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version = 1L

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE note (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL)",
            parameters = 0
        )
        return QueryResult.Unit
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion
    ): QueryResult.Value<Unit> = QueryResult.Unit
}

private fun SqlDriver.notes(): List<String> = executeQuery(
    identifier = null,
    sql = "SELECT text FROM note ORDER BY id DESC",
    mapper = { cursor ->
        val rows = mutableListOf<String>()
        while (cursor.next().value) rows += cursor.getString(0).orEmpty()
        QueryResult.Value(rows)
    },
    parameters = 0
).value

private fun SqlDriver.add(text: String) {
    execute(identifier = null, sql = "INSERT INTO note (text) VALUES (?)", parameters = 1) {
        bindString(0, text)
    }
}

private fun SqlDriver.clear() {
    execute(identifier = null, sql = "DELETE FROM note", parameters = 0)
}

@Composable
internal fun DatabaseExample() {
    val factory = rememberDatabaseDriverFactory()
    // The browser has no driver, and says so by throwing when asked for one.
    val opened = remember(factory) { runCatching { factory.createDriver(NotesSchema, "sample.db") } }
    val driver = opened.getOrNull()
    var notes by remember { mutableStateOf(emptyList<String>()) }
    var draft by remember { mutableStateOf("") }

    if (driver == null) {
        Text(
            "No database here: ${opened.exceptionOrNull()?.message ?: "the driver would not open"}.",
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        DisposableEffect(driver) { onDispose { driver.close() } }
        LaunchedEffect(driver) { notes = driver.notes() }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("New note") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        driver.add(draft.trim())
                        draft = ""
                        notes = driver.notes()
                    }
                ) { Text("Add") }
                OutlinedButton(onClick = {
                    driver.clear()
                    notes = driver.notes()
                }) { Text("Clear") }
            }
            DemoSection("${notes.size} stored") {
                NotesList(notes)
            }
        }
    }
    PlatformNote(
        "The library supplies the driver — Android's SQLite helper, the native driver on iOS, a " +
            "file-backed JDBC one on desktop — and the app supplies the schema. Notes written " +
            "here are still there after the app is closed and reopened, because each driver " +
            "opens a file rather than memory. Android and iOS hand the schema to their driver " +
            "to create or migrate with; desktop's JDBC driver is not given one, so the library " +
            "reads and stamps SQLite's `user_version` itself. The browser has no driver at all."
    )
}

@Composable
private fun NotesList(notes: List<String>) {
    if (notes.isEmpty()) Text("Nothing yet.") else OutputBox(notes.joinToString("\n"))
}
