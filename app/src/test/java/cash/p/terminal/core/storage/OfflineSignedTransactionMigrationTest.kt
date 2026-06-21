package cash.p.terminal.core.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.storage.migrations.Migration_104_105
import cash.p.terminal.core.storage.migrations.Migration_105_106
import cash.p.terminal.core.storage.migrations.Migration_106_107
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class OfflineSignedTransactionMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory database
            .callback(object : SupportSQLiteOpenHelper.Callback(START_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migrate104To107_existingRow_preservesDataAndAppliesNewColumnDefaults() {
        Migration_104_105.migrate(db)
        db.execSQL(
            """
            INSERT INTO OfflineSignedTransaction
                (accountId, txHash, blockchainTypeUid, coinCode, tokenDecimals, amount, toAddress,
                 rawHex, pcashPayload, createdAt)
            VALUES ('acc', 'hash', 'bitcoin', 'BTC', 8, '0.001', 'addr', 'deadbeef',
                'pcash:tx:v1:bitcoin:body', 1700000000000)
            """.trimIndent()
        )

        Migration_105_106.migrate(db)
        Migration_106_107.migrate(db)

        db.query(
            "SELECT status, broadcastAttempts, lastBroadcastAt, broadcastedAt, lastError, amount, " +
                "tokenQueryId, sourceTokenQueryId, coinUid, coinName, feeTokenQueryId, feeAtomic " +
                "FROM OfflineSignedTransaction"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("0.001", cursor.getString(cursor.getColumnIndexOrThrow("amount")))
            assertEquals("pending", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("broadcastAttempts")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("lastBroadcastAt")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("broadcastedAt")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("lastError")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("tokenQueryId")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("sourceTokenQueryId")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("coinUid")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("coinName")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("feeTokenQueryId")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("feeAtomic")))
        }
    }

    @Test
    fun migrate104To107_createsAccountIdCreatedAtIndex() {
        Migration_104_105.migrate(db)
        Migration_105_106.migrate(db)
        Migration_106_107.migrate(db)

        val indexNames = mutableListOf<String>()
        db.query("PRAGMA index_list(OfflineSignedTransaction)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(nameIndex))
            }
        }

        assertTrue(indexNames.contains("index_OfflineSignedTransaction_accountId_createdAt"))
    }

    @Test
    fun migrate106To107_addsTokenIdentityColumns() {
        Migration_104_105.migrate(db)
        Migration_105_106.migrate(db)

        Migration_106_107.migrate(db)

        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(OfflineSignedTransaction)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIndex))
            }
        }

        assertTrue(columns.contains("tokenQueryId"))
        assertTrue(columns.contains("sourceTokenQueryId"))
        assertTrue(columns.contains("coinUid"))
        assertTrue(columns.contains("coinName"))
        assertTrue(columns.contains("feeTokenQueryId"))
        assertTrue(columns.contains("feeAtomic"))
    }

    private companion object {
        const val START_VERSION = 104
    }
}
