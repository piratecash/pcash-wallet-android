package cash.p.terminal.wallet.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.models.BlockchainEntity
import cash.p.terminal.wallet.models.TokenEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CoinDaoRelationRaceTest {

    private lateinit var database: MarketDatabase
    private lateinit var storage: CoinStorage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MarketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = CoinStorage(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getTokens_concurrentWriterReplacesCoins_doesNotThrowRelationNonNullError() {
        val blockchain = BlockchainEntity(uid = "binance-smart-chain", name = "BSC", eip3091url = null)
        val reference = "0xabcdef0000000000000000000000000000000001"

        val coinA = Coin(uid = "coin-a", name = "Coin A", code = "AAA")
        val coinB = Coin(uid = "coin-b", name = "Coin B", code = "BBB")
        val tokenForA = TokenEntity(
            coinUid = coinA.uid,
            blockchainUid = blockchain.uid,
            type = "eip20",
            decimals = 18,
            reference = reference,
        )
        val tokenForB = TokenEntity(
            coinUid = coinB.uid,
            blockchainUid = blockchain.uid,
            type = "eip20",
            decimals = 18,
            reference = reference,
        )

        storage.update(listOf(coinA), listOf(blockchain), listOf(tokenForA))

        val stop = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val readerReady = CountDownLatch(1)

        val writer = thread(name = "race-writer", start = false) {
            readerReady.await()
            var pickA = false
            while (!stop.get() && failure.get() == null) {
                pickA = !pickA
                val coin = if (pickA) coinA else coinB
                val token = if (pickA) tokenForA else tokenForB
                try {
                    storage.update(listOf(coin), listOf(blockchain), listOf(token))
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                    return@thread
                }
            }
        }

        val reader = thread(name = "race-reader", start = false) {
            readerReady.countDown()
            while (!stop.get() && failure.get() == null) {
                try {
                    storage.getTokens(reference)
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                    return@thread
                }
            }
        }

        writer.start()
        reader.start()

        Thread.sleep(TimeUnit.SECONDS.toMillis(5))
        stop.set(true)
        writer.join(TimeUnit.SECONDS.toMillis(5))
        reader.join(TimeUnit.SECONDS.toMillis(5))

        val raceFailure = failure.get()
        if (raceFailure != null) {
            throw AssertionError(
                "Race produced exception: ${raceFailure::class.java.name}: ${raceFailure.message}",
                raceFailure,
            )
        }
    }
}
