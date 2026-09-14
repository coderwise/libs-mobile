package com.coderwise.libs.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * The desktop database is a file, in the same per-user directory the settings store writes to, so
 * that quitting the app is not the same thing as deleting everything in it. It used to be
 * `IN_MEMORY`, which every other target's driver was not: the app worked until it was restarted,
 * and then presented itself as empty.
 *
 * Directory is [DATA_DIR_PROPERTY] when set — a test, or an app that keeps its files elsewhere —
 * and `~/.mapsOn` otherwise, which is what `:settings` uses on desktop for the same reason.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DatabaseDriverFactory {
    actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver {
        val directory = File(
            System.getProperty(DATA_DIR_PROPERTY)
                ?: (System.getProperty("user.home") + "/.mapsOn")
        )
        directory.mkdirs()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(directory, name).absolutePath}")
        driver.migrateTo(schema)
        return driver
    }
}

const val DATA_DIR_PROPERTY = "com.coderwise.dataDir"

/**
 * Creates the schema in a new file, or migrates one written by an older version of the app.
 *
 * SQLite's own `user_version` holds the schema version, as it does for the Android and native
 * drivers, which are handed the schema and do this themselves — the JDBC driver is not, so the
 * version has to be read and written here. A file that has never been stamped reads 0, which is
 * exactly the "create it" case; anything lower than the schema's version is a migration.
 */
private fun SqlDriver.migrateTo(schema: SqlSchema<QueryResult.Value<Unit>>) {
    val current = executeQuery(
        identifier = null,
        sql = "PRAGMA user_version;",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        parameters = 0
    ).value
    when {
        current == 0L -> schema.create(this)
        current < schema.version -> schema.migrate(this, current, schema.version)
        else -> return
    }
    execute(identifier = null, sql = "PRAGMA user_version = ${schema.version};", parameters = 0)
}
