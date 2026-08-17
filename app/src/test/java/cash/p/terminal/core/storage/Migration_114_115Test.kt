package cash.p.terminal.core.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.storage.migrations.Migration_114_115
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_114_115Test {

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
    fun migrate114To115_createsOfflineBlockchainTableWithNullableTimestamps() {
        Migration_114_115.migrate(db)

        val notNullByColumn = mutableMapOf<String, Boolean>()
        db.query("PRAGMA table_info(OfflineBlockchain)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                notNullByColumn[cursor.getString(nameIndex)] = cursor.getInt(notNullIndex) == 1
            }
        }

        assertEquals(
            mapOf(
                "accountId" to true,
                "blockchainType" to true,
                "offline" to true,
                "enabledAt" to false,
                "lastSyncedAt" to false,
            ),
            notNullByColumn
        )
    }

    @Test
    fun migrate114To115_declaresCascadingForeignKeyToAccountRecord() {
        Migration_114_115.migrate(db)

        db.query("PRAGMA foreign_key_list(OfflineBlockchain)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals("AccountRecord", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("accountId", cursor.getString(cursor.getColumnIndexOrThrow("from")))
            assertEquals("id", cursor.getString(cursor.getColumnIndexOrThrow("to")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_update")))
        }
    }

    @Test
    fun migrate114To115_primaryKeyIsAccountAndBlockchainPair() {
        Migration_114_115.migrate(db)

        db.execSQL(
            "INSERT INTO OfflineBlockchain(accountId, blockchainType, offline, enabledAt, lastSyncedAt) " +
                "VALUES ('acc', 'zcash', 1, 1700000000000, NULL)"
        )
        db.execSQL(
            "INSERT OR REPLACE INTO OfflineBlockchain(accountId, blockchainType, offline, enabledAt, lastSyncedAt) " +
                "VALUES ('acc', 'zcash', 0, NULL, 1700000000001)"
        )
        db.execSQL(
            "INSERT INTO OfflineBlockchain(accountId, blockchainType, offline, enabledAt, lastSyncedAt) " +
                "VALUES ('acc', 'monero', 1, 1700000000002, NULL)"
        )

        db.query("SELECT COUNT(*) FROM OfflineBlockchain").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        db.query("SELECT offline FROM OfflineBlockchain WHERE accountId = 'acc' AND blockchainType = 'zcash'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
    }

    @Test
    fun migrate114To115_tableAlreadyPresent_keepsExistingRows() {
        Migration_114_115.migrate(db)
        db.execSQL(
            "INSERT INTO OfflineBlockchain(accountId, blockchainType, offline, enabledAt, lastSyncedAt) " +
                "VALUES ('acc', 'zcash', 1, 1700000000000, NULL)"
        )

        Migration_114_115.migrate(db)

        db.query("SELECT COUNT(*) FROM OfflineBlockchain").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    private companion object {
        const val START_VERSION = 114
    }
}
