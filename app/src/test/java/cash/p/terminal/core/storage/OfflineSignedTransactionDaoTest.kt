package cash.p.terminal.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.OfflineSignedTransactionStatus
import cash.p.terminal.wallet.entities.AccountRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class OfflineSignedTransactionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: OfflineSignedTransactionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.offlineSignedTransactionDao()
        // The record has a foreign key to AccountRecord, so the parent must exist first.
        database.accountsDao().insert(account())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun markBroadcastAttempt_recordAlreadyBroadcasted_doesNotDowngradeStatus() = runBlocking {
        dao.insertIfAbsent(entity())
        dao.markBroadcasted(ACCOUNT_ID, TX_HASH, TX_HASH, BROADCASTED, NOW)

        dao.markBroadcastAttempt(ACCOUNT_ID, TX_HASH, PENDING, NOW, BROADCASTED)

        assertEquals(BROADCASTED, record().status)
    }

    @Test
    fun markBroadcastFailed_recordAlreadyBroadcasted_doesNotDowngradeStatus() = runBlocking {
        dao.insertIfAbsent(entity())
        dao.markBroadcasted(ACCOUNT_ID, TX_HASH, TX_HASH, BROADCASTED, NOW)

        dao.markBroadcastFailed(ACCOUNT_ID, TX_HASH, PENDING, "boom", BROADCASTED)

        val record = record()
        assertEquals(BROADCASTED, record.status)
        assertNull(record.lastError)
    }

    @Test
    fun markBroadcastAttempt_recordPending_movesToPendingAndCountsAttempt() = runBlocking {
        dao.insertIfAbsent(entity())

        dao.markBroadcastAttempt(ACCOUNT_ID, TX_HASH, PENDING, NOW, BROADCASTED)

        val record = record()
        assertEquals(PENDING, record.status)
        assertEquals(1, record.broadcastAttempts)
    }

    @Test
    fun markBroadcasted_confirmedHashDiffers_reconcilesRecordKeyToConfirmedHash() = runBlocking {
        dao.insertIfAbsent(entity())

        dao.markBroadcasted(ACCOUNT_ID, TX_HASH, CONFIRMED_TX_HASH, BROADCASTED, NOW)

        val record = record()
        assertEquals(CONFIRMED_TX_HASH, record.txHash)
        assertEquals(BROADCASTED, record.status)
    }

    @Test
    fun reconcileBroadcasted_confirmedHashFree_renamesImportedRowToConfirmedHash() = runBlocking {
        dao.insertIfAbsent(entity())

        dao.reconcileBroadcasted(ACCOUNT_ID, TX_HASH, CONFIRMED_TX_HASH, BROADCASTED, NOW)

        val record = record()
        assertEquals(CONFIRMED_TX_HASH, record.txHash)
        assertEquals(BROADCASTED, record.status)
    }

    @Test
    fun reconcileBroadcasted_confirmedHashAlreadyExists_dropsDuplicateImportKeepingAuthoritativeRow() = runBlocking {
        // The same transaction is present twice: the authoritative row already broadcasted under its
        // real hash, plus a duplicate import keyed by a different (spoofed) hash.
        dao.insertIfAbsent(entity(txHash = CONFIRMED_TX_HASH, status = BROADCASTED))
        dao.insertIfAbsent(entity(txHash = TX_HASH, status = PENDING))

        dao.reconcileBroadcasted(ACCOUNT_ID, TX_HASH, CONFIRMED_TX_HASH, BROADCASTED, NOW)

        val records = records()
        assertEquals(1, records.size)
        assertEquals(CONFIRMED_TX_HASH, records.single().txHash)
        assertEquals(BROADCASTED, records.single().status)
    }

    private suspend fun record() = dao.observe(ACCOUNT_ID).first().single()

    private suspend fun records() = dao.observe(ACCOUNT_ID).first()

    private fun account() = AccountRecord(
        id = ACCOUNT_ID,
        name = "Test",
        type = "mnemonic",
        origin = "Restored",
        isBackedUp = true,
        isFileBackedUp = false,
        words = null,
        passphrase = null,
        key = null,
        level = 0,
    )

    private fun entity(
        txHash: String = TX_HASH,
        status: String = PENDING,
    ) = OfflineSignedTransactionEntity(
        accountId = ACCOUNT_ID,
        txHash = txHash,
        blockchainTypeUid = "bitcoin",
        tokenQueryId = "bitcoin|native",
        sourceTokenQueryId = "bitcoin|native",
        coinUid = "bitcoin",
        coinCode = "BTC",
        coinName = "Bitcoin",
        tokenDecimals = 8,
        amount = "0.001",
        feeTokenQueryId = null,
        feeAtomic = null,
        solanaBlockHash = null,
        solanaLastValidBlockHeight = null,
        tonValidUntil = null,
        tonSenderAddress = null,
        tonSeqno = null,
        toAddress = "addr",
        rawHex = "deadbeef",
        pcashPayload = "pcash:tx:v1:bitcoin:body",
        createdAt = NOW,
        status = status,
        broadcastAttempts = 0,
        lastBroadcastAt = null,
        broadcastedAt = null,
        lastError = null,
    )

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val TX_HASH = "tx-imported"
        const val CONFIRMED_TX_HASH = "tx-confirmed"
        const val NOW = 1_700_000_000_000L
        val PENDING = OfflineSignedTransactionStatus.Pending.value
        val BROADCASTED = OfflineSignedTransactionStatus.Broadcasted.value
    }
}
