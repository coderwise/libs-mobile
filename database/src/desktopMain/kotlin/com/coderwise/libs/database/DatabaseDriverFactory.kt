package com.coderwise.libs.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * The desktop database is a file under [dataDir], so that quitting the app is not the same thing as
 * deleting everything in it. It used to be `IN_MEMORY`, which every other target's driver was not:
 * the app worked until it was restarted and then presented itself as empty.
 *
 * Where that directory is, is the application's business and not this library's — every app that
 * depends on it keeps its files somewhere of its own — so it is passed in. See
 * `databaseDriverModule`, which asks the app graph for it under [DATA_DIR_QUALIFIER].
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DatabaseDriverFactory(private val dataDir: File) {
    actual fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver {
        dataDir.mkdirs()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(dataDir, name).absolutePath}")
        driver.migrateTo(schema)
        return driver
    }
}

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
