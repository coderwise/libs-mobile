package com.coderwise.libs.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseDriverFactoryTest {

    private lateinit var dataDir: File

    /** The schema of a one-table database, standing in for whatever an app publishes. */
    private fun schema(version: Long) = object : SqlSchema<QueryResult.Value<Unit>> {
        override val version = version

        override fun create(driver: app.cash.sqldelight.db.SqlDriver): QueryResult.Value<Unit> {
            driver.execute(null, "CREATE TABLE Note(id TEXT NOT NULL PRIMARY KEY);", 0)
            return QueryResult.Unit
        }

        override fun migrate(
            driver: app.cash.sqldelight.db.SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: app.cash.sqldelight.db.AfterVersion
        ): QueryResult.Value<Unit> {
            driver.execute(null, "ALTER TABLE Note ADD COLUMN body TEXT;", 0)
            return QueryResult.Unit
        }
    }

    @BeforeTest
    fun setUp() {
        dataDir = File.createTempFile("coderwise-db", "").let { file ->
            file.delete()
            file.also { it.mkdirs() }
        }
        System.setProperty(DATA_DIR_PROPERTY, dataDir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty(DATA_DIR_PROPERTY)
        dataDir.deleteRecursively()
    }

    @Test
    fun `rows written by one run are there for the next`() {
        val first = DatabaseDriverFactory().createDriver(schema(1), "test.db")
        first.execute(null, "INSERT INTO Note(id) VALUES ('a');", 0)
        first.close()

        val second = DatabaseDriverFactory().createDriver(schema(1), "test.db")
        assertEquals(listOf("a"), second.noteIds())
        second.close()
    }

    @Test
    fun `a file from an older version is migrated rather than recreated`() {
        val first = DatabaseDriverFactory().createDriver(schema(1), "test.db")
        first.execute(null, "INSERT INTO Note(id) VALUES ('a');", 0)
        first.close()

        val second = DatabaseDriverFactory().createDriver(schema(2), "test.db")
        // The row survived, and the column the migration adds is there to be written.
        second.execute(null, "UPDATE Note SET body = 'kept' WHERE id = 'a';", 0)
        assertEquals(listOf("a"), second.noteIds())
        assertEquals(2L, second.userVersion())
        second.close()
    }

    @Test
    fun `the database lands in the data directory under the name it was asked for`() {
        DatabaseDriverFactory().createDriver(schema(1), "named.db").close()

        assertEquals(true, File(dataDir, "named.db").exists())
    }

    private fun app.cash.sqldelight.db.SqlDriver.noteIds(): List<String> = executeQuery(
        identifier = null,
        sql = "SELECT id FROM Note ORDER BY id;",
        mapper = { cursor ->
            val ids = mutableListOf<String>()
            while (cursor.next().value) cursor.getString(0)?.let { ids += it }
            QueryResult.Value(ids.toList())
        },
        parameters = 0
    ).value

    private fun app.cash.sqldelight.db.SqlDriver.userVersion(): Long = executeQuery(
        identifier = null,
        sql = "PRAGMA user_version;",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        parameters = 0
    ).value
}
