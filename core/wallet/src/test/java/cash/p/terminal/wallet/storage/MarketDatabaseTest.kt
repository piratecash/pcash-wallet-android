package cash.p.terminal.wallet.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MarketDatabaseTest {

    @Test
    fun `loadInitialCoins returns correct count`() {
        val context = mockk<Context>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val file = File("src/main/assets/initial_coins_list")
        val inputStream = file.inputStream()

        every { context.assets.open("initial_coins_list") } returns inputStream

        val marketDatabase = MarketDatabase.Companion
        val count = marketDatabase.loadInitialCoins(db, context)

        assertEquals(17534, count)
    }
}