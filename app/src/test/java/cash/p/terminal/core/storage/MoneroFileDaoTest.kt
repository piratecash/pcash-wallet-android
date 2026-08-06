package cash.p.terminal.core.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.wallet.entities.AccountRecord
import cash.p.terminal.wallet.entities.SecretString
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.IEncryptionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MoneroFileDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MoneroFileDao

    @Before
    fun setUp() {
        CoreApp.encryptionManager = object : IEncryptionManager {
            override fun encrypt(data: String) = data
            override fun decrypt(data: String) = data
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.moneroFileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_parentAccountExists_persistsRecord() = runBlocking {
        database.accountsDao().insert(account())

        dao.insert(record())

        assertEquals(record(), dao.getAssociatedRecord(ACCOUNT_ID))
    }

    @Test
    fun insert_parentAccountMissing_rejectsRecord() = runBlocking {
        assertFailsWith<SQLiteConstraintException> {
            dao.insert(record())
        }
        Unit
    }

    private fun account() = AccountRecord(
        id = ACCOUNT_ID,
        name = "Trezor",
        type = "trezor",
        origin = "Created",
        isBackedUp = false,
        isFileBackedUp = false,
        words = null,
        passphrase = null,
        key = null,
        level = 0,
    )

    private fun record() = MoneroFileRecord(
        accountId = ACCOUNT_ID,
        fileName = SecretString("trezor-$ACCOUNT_ID"),
        password = SecretString("password"),
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
