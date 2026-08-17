package cash.p.terminal.modules.transactions

import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.adapters.BaseEvmAdapter
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import com.m2049r.xmrwallet.model.TransactionInfo
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.ethereumkit.models.Transaction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class TransactionViewItemFactoryCacheTest {

    private companion object {
        const val PENDING_UID = "4392cdda-870d-46d7-8cc8-c06ac0be4bd3"
        const val TX_HASH = "0e850ae3cb3963672d2880a4940172732ccab477f0ea3fc93a8914e912c670ad"
        const val PENDING_TIMESTAMP = 1_779_766_789L
        const val BRIDGE_ADDRESS = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436"

        val ZEC_AMOUNT: BigDecimal = BigDecimal("0.01285429")
    }

    private val addressLabelManager = mockk<AddressLabelManager>()
    private val contactsRepository = mockk<ContactsRepository>()
    private val balanceHiddenManager = mockk<BalanceHiddenManager>()
    private val swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val swapTransactionMatcher = mockk<SwapTransactionMatcher>(relaxed = true)
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>()
    private val poisonAddressManager = mockk<PoisonAddressManager>()
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val appNumberFormatter = mockk<IAppNumberFormatter>()

    private lateinit var factory: TransactionViewItemFactory

    @Before
    fun setUp() {
        mockkObject(App)
        mockkObject(DateHelper)
        mockkObject(Translator)

        every { App.numberFormatter } returns appNumberFormatter
        every { DateHelper.getOnlyTime(any()) } returns "12:00"
        every { DateHelper.shortDate(any(), any(), any()) } returns "Apr 6"
        every { appNumberFormatter.formatCoinShort(any(), any(), any()) } answers {
            val value = firstArg<BigDecimal>().stripTrailingZeros().toPlainString()
            val code = secondArg<String?>().orEmpty()
            "$code:$value"
        }
        every { appNumberFormatter.formatFiatShort(any(), any(), any()) } returns "$0"

        every { addressLabelManager.label(any(), any()) } returns null
        every { contactsRepository.getContactsFiltered(any(), addressQuery = any()) } returns emptyList()
        every { balanceHiddenManager.balanceHidden } returns false
        every { balanceHiddenManager.isTransactionInfoHidden(any()) } returns false
        every { balanceHiddenManager.isTransactionInfoHiddenForWallet(any(), any()) } returns false
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        every { swapTransactionMatcher.findMatchingSwap(any()) } returns null
        coEvery { poisonAddressManager.getPoisonStatus(any<TransactionRecord>()) } returns PoisonStatus.BLOCKCHAIN

        factory = TransactionViewItemFactory(
            addressMetadataManager = AddressMetadataManager(
                contactsRepository = contactsRepository,
                addressLabelManager = addressLabelManager,
            ),
            balanceHiddenManager = balanceHiddenManager,
            swapProviderTransactionsStorage = swapProviderTransactionsStorage,
            swapTransactionMatcher = swapTransactionMatcher,
            numberFormatter = numberFormatter,
            marketKit = marketKit,
            localStorage = localStorage,
            poisonAddressManager = poisonAddressManager,
            accountManager = accountManager,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun convertToViewItemCached_updatedListData_rebuildsCachedItem() = runTest {
        val initialRecord = createUnknownSwapRecord(
            uid = "swap-uid",
            valueOut = null,
        )
        val updatedRecord = createUnknownSwapRecord(
            uid = "swap-uid",
            valueOut = TransactionValue.TokenValue(
                tokenName = "USDT",
                tokenCode = "USDT",
                tokenDecimals = 6,
                value = BigDecimal("42"),
            ),
        )

        val initialItem = TransactionItem(
            record = initialRecord,
            currencyValue = null,
            lastBlockInfo = null,
            nftMetadata = emptyMap(),
        )
        val updatedItem = initialItem.withUpdatedListData(
            record = updatedRecord,
        )

        val initialViewItem = factory.convertToViewItemCached(initialItem)
        val updatedViewItem = factory.convertToViewItemCached(updatedItem)

        assertNotEquals(initialItem.cacheVersion, updatedItem.cacheVersion)
        assertNull(initialViewItem.primaryValue)
        assertEquals("+USDT:42", updatedViewItem.primaryValue?.value)
    }

    @Test
    fun convertToViewItemCached_detailsOnlyCopy_reusesCachedItem() = runTest {
        val record = createUnknownSwapRecord(
            uid = "swap-uid",
            valueOut = null,
        )
        val initialItem = TransactionItem(
            record = record,
            currencyValue = null,
            lastBlockInfo = null,
            nftMetadata = emptyMap(),
        )
        val detailsItem = initialItem.copy(
            changeNowTransactionId = "tx-id",
        )

        val initialViewItem = factory.convertToViewItemCached(initialItem)
        val detailsViewItem = factory.convertToViewItemCached(detailsItem)

        assertEquals(initialItem.cacheVersion, detailsItem.cacheVersion)
        assertSame(initialViewItem, detailsViewItem)
    }

    @Test
    fun convertToViewItemCached_evmSwap_marksAsSwap() = runTest {
        val record = createUnknownSwapRecord(
            uid = "pancake-swap-uid",
            valueOut = null,
            transactionRecordType = TransactionRecordType.EVM_SWAP,
        )

        val viewItem = factory.convertToViewItemCached(createTransactionItem(record))

        assertTrue(viewItem.isSwap)
    }

    @Test
    fun convertToViewItemCached_finishedProviderWithUnconfirmedMoneroIncoming_preservesOnChainProgress() = runTest {
        val record = createMoneroIncomingRecord(
            confirmations = 7,
            confirmationsThreshold = TransactionInfo.CONFIRMATION,
        )
        val swap = createSwapProviderTransaction(outgoingRecordUid = null).copy(
            status = "finished",
            provider = SwapProvider.EXOLIX,
            coinUidOut = "monero",
            blockchainTypeOut = BlockchainType.Monero.uid,
            amountOut = BigDecimal("0.01226"),
        )

        val viewItem = factory.convertToViewItemCached(
            transactionItem = createTransactionItem(record),
            matchedSwap = swap,
        )

        assertEquals(
            Translator.getString(R.string.transaction_swap_status_confirming),
            viewItem.title,
        )
        assertEquals(
            7f / TransactionInfo.CONFIRMATION,
            viewItem.progress ?: 0f,
            0.0001f,
        )
    }

    @Test
    fun convertToViewItemCached_finishedProviderWithUnconfirmedEvmIncoming_usesEvmConfirmationThreshold() = runTest {
        val record = createEvmTransferRecord(
            address = BRIDGE_ADDRESS,
            transactionRecordType = TransactionRecordType.EVM_INCOMING,
            blockNumber = 100L,
        )
        val swap = createSwapProviderTransaction(outgoingRecordUid = null).copy(
            status = "finished",
            provider = SwapProvider.EXOLIX,
            coinUidOut = "cosanta",
            blockchainTypeOut = BlockchainType.BinanceSmartChain.uid,
            amountOut = BigDecimal.ONE,
        )

        val viewItem = factory.convertToViewItemCached(
            transactionItem = createTransactionItem(
                record = record,
                lastBlockInfo = LastBlockInfo(height = 106),
            ),
            matchedSwap = swap,
        )

        assertEquals(
            7f / BaseEvmAdapter.confirmationsThreshold,
            viewItem.progress ?: 0f,
            0.0001f,
        )
    }

    @Test
    fun convertToViewItemCached_evmIncoming_usesAddressLabel() = runTest {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returns "Token Bridge"
        stubAddressTranslation("Token Bridge")
        val record = createEvmTransferRecord(
            address = BRIDGE_ADDRESS,
            transactionRecordType = TransactionRecordType.EVM_INCOMING,
        )

        val viewItem = factory.convertToViewItemCached(createTransactionItem(record))

        assertTrue(viewItem.subtitle.contains("Token Bridge"))
        verify(exactly = 1) {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        }
    }

    @Test
    fun convertToViewItemCached_evmOutgoing_usesAddressLabel() = runTest {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returns "Token Bridge"
        stubAddressTranslation("Token Bridge")
        val record = createEvmTransferRecord(
            address = BRIDGE_ADDRESS,
            transactionRecordType = TransactionRecordType.EVM_OUTGOING,
        )

        val viewItem = factory.convertToViewItemCached(createTransactionItem(record))

        assertTrue(viewItem.subtitle.contains("Token Bridge"))
        verify(exactly = 1) {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        }
    }

    @Test
    fun convertToViewItemCached_evmAddressMatchesContact_usesContactName() = runTest {
        val contact = mockk<Contact> {
            every { name } returns "Bridge Contact"
        }
        every {
            contactsRepository.getContactsFiltered(
                BlockchainType.BinanceSmartChain,
                addressQuery = BRIDGE_ADDRESS,
            )
        } returns listOf(contact)
        stubAddressTranslation("Bridge Contact")
        val record = createEvmTransferRecord(
            address = BRIDGE_ADDRESS,
            transactionRecordType = TransactionRecordType.EVM_INCOMING,
        )

        val viewItem = factory.convertToViewItemCached(createTransactionItem(record))

        assertTrue(viewItem.subtitle.contains("Bridge Contact"))
        verify(exactly = 0) {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        }
    }

    @Test
    fun convertToViewItemCached_cacheClearedAfterLabelChange_usesUpdatedLabel() = runTest {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returnsMany listOf("Old Bridge", "Token Bridge")
        stubAddressTranslation("Old Bridge")
        stubAddressTranslation("Token Bridge")
        val record = createEvmTransferRecord(
            address = BRIDGE_ADDRESS,
            transactionRecordType = TransactionRecordType.EVM_INCOMING,
        )
        val transactionItem = createTransactionItem(record)

        val initialViewItem = factory.convertToViewItemCached(transactionItem)
        factory.clearCache()
        val updatedViewItem = factory.convertToViewItemCached(transactionItem)

        assertTrue(initialViewItem.subtitle.contains("Old Bridge"))
        assertTrue(updatedViewItem.subtitle.contains("Token Bridge"))
        verify(exactly = 2) {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        }
    }

    @Test
    fun convertToViewItemCached_pendingOutgoingFallbackWithHash_persistsTransactionHash() = runTest {
        val record = createPendingRecord(
            transactionHash = TX_HASH,
        )
        val swap = createSwapProviderTransaction(outgoingRecordUid = null)

        every { swapProviderTransactionsStorage.getByOutgoingRecordUid(TX_HASH) } returns null
        stubOutgoingFallback(swap)

        val viewItem = factory.convertToViewItemCached(createTransactionItem(record))

        assertEquals(swap.transactionId, viewItem.changeNowTransactionId)
        assertTrue(viewItem.isSwap)
        verify {
            swapProviderTransactionsStorage.setOutgoingRecordUid(
                date = swap.date,
                outgoingRecordUid = TX_HASH,
            )
        }
        verify(exactly = 0) {
            swapProviderTransactionsStorage.setOutgoingRecordUid(
                date = swap.date,
                outgoingRecordUid = record.uid,
            )
        }
    }

    @Test
    fun convertToViewItemCached_pendingOutgoingMatchedByHash_doesNotPersistPendingUid() = runTest {
        val record = createPendingRecord(
            transactionHash = TX_HASH,
        )
        val swap = createSwapProviderTransaction(outgoingRecordUid = TX_HASH)

        every { swapProviderTransactionsStorage.getByOutgoingRecordUid(TX_HASH) } returns swap
        stubOutgoingFallback(swap)

        val viewItem = factory.convertToViewItemCached(createTransactionItem(record))

        assertEquals(swap.transactionId, viewItem.changeNowTransactionId)
        verify(exactly = 0) {
            swapProviderTransactionsStorage.setOutgoingRecordUid(
                date = any(),
                outgoingRecordUid = any(),
            )
        }
    }

    @Test
    fun convertToViewItemCached_pendingOutgoingFallbackWithoutHash_doesNotPersistPendingUid() = runTest {
        val record = createPendingRecord(
            transactionHash = "",
        )
        val swap = createSwapProviderTransaction(outgoingRecordUid = null)

        every { swapProviderTransactionsStorage.getByOutgoingRecordUid(record.uid) } returns null
        stubOutgoingFallback(swap)

        val viewItem = factory.convertToViewItemCached(createTransactionItem(record))

        assertEquals(swap.transactionId, viewItem.changeNowTransactionId)
        verify(exactly = 0) {
            swapProviderTransactionsStorage.setOutgoingRecordUid(
                date = any(),
                outgoingRecordUid = any(),
            )
        }
    }

    private fun createUnknownSwapRecord(
        uid: String,
        valueOut: TransactionValue?,
        transactionRecordType: TransactionRecordType = TransactionRecordType.EVM_UNKNOWN_SWAP,
    ): EvmTransactionRecord {
        return EvmTransactionRecord(
            transaction = createEvmTransaction(uid),
            token = mockk<Token>(relaxed = true),
            source = createBscSource(),
            protected = false,
            transactionRecordType = transactionRecordType,
            exchangeAddress = "0xpancakeswap_router",
            valueIn = TransactionValue.TokenValue(
                tokenName = "BNB",
                tokenCode = "BNB",
                tokenDecimals = 18,
                value = BigDecimal("-1"),
            ),
            valueOut = valueOut,
        )
    }

    private fun createEvmTransferRecord(
        address: String,
        transactionRecordType: TransactionRecordType,
        blockNumber: Long? = null,
    ): EvmTransactionRecord {
        val incoming = transactionRecordType == TransactionRecordType.EVM_INCOMING

        return EvmTransactionRecord(
            from = address.takeIf { incoming },
            to = address.takeUnless { incoming },
            transaction = createEvmTransaction(
                uid = "evm-transfer-$transactionRecordType",
                blockNumber = blockNumber,
            ),
            token = mockk(relaxed = true),
            source = createBscSource(),
            protected = false,
            transactionRecordType = transactionRecordType,
            value = TransactionValue.TokenValue(
                tokenName = "Cosanta",
                tokenCode = "COSA",
                tokenDecimals = 8,
                value = if (incoming) BigDecimal.ONE else BigDecimal.ONE.negate(),
            ),
        )
    }

    private fun createEvmTransaction(
        uid: String,
        blockNumber: Long? = null,
    ) = mockk<Transaction>(relaxed = true) {
        every { hashString } returns uid
        every { transactionIndex } returns 0
        every { this@mockk.blockNumber } returns blockNumber
        every { timestamp } returns 1_000L
        every { isFailed } returns false
    }

    private fun createMoneroIncomingRecord(
        confirmations: Long,
        confirmationsThreshold: Int,
    ): MoneroTransactionRecord {
        val token = Token(
            coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
            blockchain = Blockchain(BlockchainType.Monero, "Monero", null),
            type = TokenType.Native,
            decimals = 12,
        )
        return MoneroTransactionRecord(
            uid = TX_HASH,
            transactionHash = TX_HASH,
            blockHeight = 1,
            confirmationsThreshold = confirmationsThreshold,
            timestamp = PENDING_TIMESTAMP,
            source = TransactionSource(
                blockchain = token.blockchain,
                account = mockk<Account>(relaxed = true),
                meta = null,
            ),
            transactionRecordType = TransactionRecordType.MONERO_INCOMING,
            token = token,
            to = "monero-address",
            amount = BigDecimal("0.01226"),
            fee = TransactionValue.CoinValue(token, BigDecimal.ZERO),
            subaddressLabel = null,
            isPending = false,
            confirmations = confirmations,
        )
    }

    private fun createBscSource() = mockk<TransactionSource>(relaxed = true) {
        every { blockchain } returns mockk(relaxed = true) {
            every { type } returns BlockchainType.BinanceSmartChain
        }
    }

    private fun createPendingRecord(
        transactionHash: String,
    ): PendingTransactionRecord {
        val token = createZcashToken()

        return PendingTransactionRecord(
            uid = PENDING_UID,
            transactionHash = transactionHash,
            timestamp = PENDING_TIMESTAMP,
            source = TransactionSource(
                blockchain = token.blockchain,
                account = mockk<Account>(relaxed = true),
                meta = null,
            ),
            token = token,
            amount = ZEC_AMOUNT,
            toAddress = "t1Js8mMvZzCY2gUpTpKcNetJrMihqaPbSXF",
            fromAddress = "from-address",
            expiresAt = Long.MAX_VALUE,
            memo = null,
        )
    }

    private fun createZcashToken() = Token(
        coin = Coin(
            uid = "zcash",
            name = "Zcash",
            code = "ZEC",
        ),
        blockchain = Blockchain(
            type = BlockchainType.Zcash,
            name = "Zcash",
            eip3091url = null,
        ),
        type = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent),
        decimals = 8,
    )

    private fun createSwapProviderTransaction(
        outgoingRecordUid: String?,
    ) = SwapProviderTransaction(
        date = 1_000L,
        outgoingRecordUid = outgoingRecordUid,
        transactionId = "b0dd9ce8a57c7e",
        status = "new",
        provider = SwapProvider.CHANGENOW,
        coinUidIn = "zcash",
        blockchainTypeIn = BlockchainType.Zcash.uid,
        amountIn = ZEC_AMOUNT,
        addressIn = "t1Js8mMvZzCY2gUpTpKcNetJrMihqaPbSXF",
        coinUidOut = "binancecoin",
        blockchainTypeOut = BlockchainType.BinanceSmartChain.uid,
        amountOut = BigDecimal("0.01"),
        addressOut = "0xRecipient",
        accountId = "test-account",
    )

    private fun stubOutgoingFallback(swap: SwapProviderTransaction) {
        every {
            swapProviderTransactionsStorage.getByCoinUidIn(
                coinUid = "zcash",
                blockchainType = BlockchainType.Zcash.uid,
                amountIn = ZEC_AMOUNT,
                timestamp = PENDING_TIMESTAMP * 1_000,
            )
        } returns swap
    }

    private fun stubAddressTranslation(value: String) {
        every { Translator.getString(any(), value) } returns value
    }

    private fun createTransactionItem(
        record: TransactionRecord,
        lastBlockInfo: LastBlockInfo? = null,
    ) = TransactionItem(
        record = record,
        currencyValue = null,
        lastBlockInfo = lastBlockInfo,
        nftMetadata = emptyMap(),
    )
}
