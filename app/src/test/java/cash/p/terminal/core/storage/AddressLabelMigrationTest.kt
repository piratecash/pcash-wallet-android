package cash.p.terminal.core.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.storage.migrations.Migration_113_114
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AddressLabelMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(113) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        db = helper.writableDatabase
        createVersion113Tables()
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migrate113To114_existingLabels_movesToNormalizedEvmLegacyScope() {
        db.execSQL(
            """
            INSERT INTO EvmAddressLabel(address, label)
            VALUES ('0x579fedB9253ccA1b3114d5e2fA44F8158d61e436', 'Token Bridge')
            """.trimIndent()
        )

        Migration_113_114.migrate(db)

        db.query(
            "SELECT scope, normalizedAddress, source, label FROM AddressLabel"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("evm", cursor.getString(cursor.getColumnIndexOrThrow("scope")))
            assertEquals(
                "0x579fedb9253cca1b3114d5e2fa44f8158d61e436",
                cursor.getString(cursor.getColumnIndexOrThrow("normalizedAddress")),
            )
            assertEquals(
                "LEGACY_API",
                cursor.getString(cursor.getColumnIndexOrThrow("source")),
            )
            assertEquals(
                "Token Bridge",
                cursor.getString(cursor.getColumnIndexOrThrow("label")),
            )
        }
        assertFalse(tableExists("EvmAddressLabel"))
    }

    @Test
    fun migrate113To114_methodLabelsAndSyncState_preservesExistingRows() {
        db.execSQL("INSERT INTO EvmMethodLabel(methodId, label) VALUES ('0xaabbccdd', 'Transfer')")
        db.execSQL("INSERT INTO SyncerState(`key`, value) VALUES ('timestamp', '11')")

        Migration_113_114.migrate(db)

        assertEquals("Transfer", singleValue("SELECT label FROM EvmMethodLabel"))
        assertEquals("11", singleValue("SELECT value FROM SyncerState"))
    }

    private fun createVersion113Tables() {
        db.execSQL(
            """
            CREATE TABLE EvmAddressLabel (
                address TEXT NOT NULL,
                label TEXT NOT NULL,
                PRIMARY KEY(address)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE EvmMethodLabel (
                methodId TEXT NOT NULL,
                label TEXT NOT NULL,
                PRIMARY KEY(methodId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE SyncerState (
                `key` TEXT NOT NULL,
                value TEXT NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent()
        )
    }

    private fun tableExists(table: String): Boolean {
        return db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { it.moveToFirst() }
    }

    private fun singleValue(query: String): String {
        return db.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
    }
}
