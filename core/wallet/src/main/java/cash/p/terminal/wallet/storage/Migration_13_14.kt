package cash.p.terminal.wallet.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migration_13_14 : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfNotExists(db, "Coin", "image", "TEXT")
        addColumnIfNotExists(db, "Coin", "priority", "INTEGER")

        migrateCoinPriceTable(db)
    }

    private fun migrateCoinPriceTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE CoinPrice_new (
                coinUid TEXT NOT NULL,
                currencyCode TEXT NOT NULL,
                value TEXT NOT NULL,
                diff1h TEXT,
                diff24h TEXT,
                diff7d TEXT,
                diff30d TEXT,
                diff1y TEXT,
                diffAll TEXT,
                timestamp INTEGER NOT NULL,
                PRIMARY KEY(coinUid, currencyCode)
            )
        """.trimIndent()
        )

        database.execSQL(
            """
            INSERT INTO CoinPrice_new (coinUid, currencyCode, value, diff24h, timestamp)
            SELECT coinUid, currencyCode, value, 
                   CASE WHEN diff1d IS NOT NULL THEN diff1d ELSE diff24h END as diff24h,
                   timestamp
            FROM CoinPrice
        """.trimIndent()
        )

        database.execSQL("DROP TABLE CoinPrice")

        database.execSQL("ALTER TABLE CoinPrice_new RENAME TO CoinPrice")
    }

    private fun addColumnIfNotExists(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String
    ) {
        val cursor = database.query("PRAGMA table_info($tableName)")
        var exists = false
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (name.equals(columnName, ignoreCase = true)) {
                exists = true
                break
            }
        }
        cursor.close()
        if (!exists) {
            database.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnType")
        }
    }
}