package cash.p.terminal.wallet.storage

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

private val insertTablePattern = Regex("""^INSERT OR REPLACE INTO (\w+) """)

/**
 * Locates the checked-in initial coins dump. Resolves both the module-relative working
 * directory (Gradle test task) and the repo-root working directory (other test runners).
 */
internal fun initialCoinsFile(): File {
    val moduleRelativeFile = File("src/main/assets/initial_coins_list")
    if (moduleRelativeFile.exists()) {
        return moduleRelativeFile
    }

    return File("core/wallet/src/main/assets/initial_coins_list")
}

/**
 * Validates a coins-list SQL dump against a real in-memory SQLite database, catching
 * regressions a mocked database cannot: statements that fail to execute, INSERT OR REPLACE
 * silently collapsing duplicate primary keys, and dangling token -> coin/blockchain
 * references. Requires a Robolectric test runner for the native SQLite engine.
 */
internal fun validateDumpSql(dump: String) {
    val statements = dump.lines().filter { it.isNotBlank() }

    val database = SQLiteDatabase.create(null)
    try {
        database.beginTransaction()
        try {
            statements.forEach { statement -> database.execSQL(statement) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        assertRowCountsMatchInserts(database, statements)
        assertNoForeignKeyViolations(database)
    } finally {
        database.close()
    }
}

private fun assertRowCountsMatchInserts(database: SQLiteDatabase, statements: List<String>) {
    val insertCountsByTable = statements
        .mapNotNull { insertTablePattern.find(it)?.groupValues?.get(1) }
        .groupingBy { it }
        .eachCount()

    insertCountsByTable.forEach { (table, expectedCount) ->
        database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals("Row count mismatch for $table (duplicate primary key?)", expectedCount, cursor.getInt(0))
        }
    }
}

private fun assertNoForeignKeyViolations(database: SQLiteDatabase) {
    database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
        assertTrue("Dangling foreign key references found", cursor.count == 0)
    }
}
