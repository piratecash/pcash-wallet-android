package cash.p.terminal.wallet.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import cash.p.terminal.wallet.storage.MarketDatabase.Companion.MarketDatabaseCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MarketDatabaseTest {

    private val db = mockk<SupportSQLiteDatabase>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val callback = MarketDatabaseCallback(context)

    @Test
    fun loadInitialCoins_returnsCorrectCount() {
        val file = File("src/main/assets/initial_coins_list")
        val inputStream = file.inputStream()

        every { context.assets.open("initial_coins_list") } returns inputStream

        val count = MarketDatabase.loadInitialCoins(db, context)

        assertEquals(17534, count)
    }

    @Test
    fun onCreate_doesNotEnableForeignKeys() {
        callback.onCreate(db)

        verify(exactly = 0) { db.setForeignKeyConstraintsEnabled(any()) }
    }

    @Test
    fun onDestructiveMigration_doesNotEnableForeignKeys() {
        callback.onDestructiveMigration(db)

        verify(exactly = 0) { db.setForeignKeyConstraintsEnabled(any()) }
    }

    @Test
    fun onOpen_enablesForeignKeys() {
        callback.onOpen(db)

        verify(exactly = 1) { db.setForeignKeyConstraintsEnabled(true) }
    }
}