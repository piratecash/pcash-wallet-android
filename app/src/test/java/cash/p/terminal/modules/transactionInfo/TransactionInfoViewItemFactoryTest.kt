package cash.p.terminal.modules.transactionInfo

import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(application = TestCoreApp::class)
class TransactionInfoViewItemFactoryTest {

    private val addressLabelManager = mockk<AddressLabelManager>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true)

    @Before
    fun setUp() {
        stopKoin()
        CoreApp.instance = RuntimeEnvironment.getApplication() as CoreApp
        startKoin {
            modules(
                module {
                    single { numberFormatter }
                    single { contactsRepository }
                    single { addressLabelManager }
                    single {
                        AddressMetadataManager(
                            contactsRepository = contactsRepository,
                            addressLabelManager = addressLabelManager,
                        )
                    }
                }
            )
        }
        every { numberFormatter.formatCoinFull(any(), any(), any()) } answers {
            "${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()} ${secondArg<String>()}"
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun getViewItemSections_offlineRawWithoutMetadata_showsPlaceholders() {
        val record = pendingRecord(toAddress = "")
        val item = transactionInfoItem(
            record = record,
            offlineStatus = ColoredValue("Sent", ColorName.Remus),
        )

        val sections = TransactionInfoViewItemFactory(
            offlineOperationGate = mockk<OfflineOperationGate>(relaxed = true),
            wallet = null,
            blockchainType = BlockchainType.Bitcoin,
        ).getViewItemSections(item)

        val amount = sections.first().first() as TransactionInfoViewItem.Amount
        val recipient = sections.first()[1] as TransactionInfoViewItem.Value
        assertEquals("---", amount.coinValue.value)
        assertEquals("---", amount.fiatValue.value)
        assertEquals("---", recipient.value)
    }

    @Test
    fun getReceiveSectionItems_knownAddress_showsLabelInTitleAndKeepsFullAddress() {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returns "Token Bridge"

        val items = TransactionViewItemFactoryHelper.getReceiveSectionItems(
            value = tokenValue(),
            fromAddress = BRIDGE_ADDRESS,
            toAddress = null,
            coinPrice = null,
            hideAmount = false,
            blockchainType = BlockchainType.BinanceSmartChain,
        )

        val address = items.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(BRIDGE_ADDRESS, address.value)
        assertEquals(false, address.showAdd)
        assertEquals(true, address.title.endsWith(" · Token Bridge"))
        assertEquals(true, address.collapseAddress)
    }

    @Test
    fun getSendSectionItems_knownAddress_showsLabelInTitleAndKeepsFullAddress() {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returns "Token Bridge"

        val items = TransactionViewItemFactoryHelper.getSendSectionItems(
            value = tokenValue(),
            toAddress = listOf(BRIDGE_ADDRESS),
            coinPrice = null,
            hideAmount = false,
            blockchainType = BlockchainType.BinanceSmartChain,
        )

        val address = items.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(BRIDGE_ADDRESS, address.value)
        assertEquals(false, address.showAdd)
        assertEquals(true, address.title.endsWith(" · Token Bridge"))
    }

    @Test
    fun getApproveSectionItems_knownSpender_showsLabelInTitle() {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returns "Token Bridge"

        val items = TransactionViewItemFactoryHelper.getApproveSectionItems(
            value = tokenValue(),
            coinPrice = null,
            spenderAddress = BRIDGE_ADDRESS,
            hideAmount = false,
            blockchainType = BlockchainType.BinanceSmartChain,
        )

        val address = items.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(true, address.title.endsWith(" · Token Bridge"))
        assertEquals(false, address.showAdd)
    }

    @Test
    fun getSendSectionItems_contactAndLabelExist_keepsContactPriority() {
        val contact = mockk<Contact>(relaxed = true)
        every {
            contactsRepository.getContactsFiltered(
                BlockchainType.BinanceSmartChain,
                addressQuery = BRIDGE_ADDRESS,
            )
        } returns listOf(contact)

        val items = TransactionViewItemFactoryHelper.getSendSectionItems(
            value = tokenValue(),
            toAddress = listOf(BRIDGE_ADDRESS),
            coinPrice = null,
            hideAmount = false,
            blockchainType = BlockchainType.BinanceSmartChain,
        )

        val address = items.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(false, address.title.contains("Token Bridge"))
        assertEquals(false, address.showAdd)
        assertEquals(false, address.collapseAddress)
        assertEquals(
            contact,
            items.filterIsInstance<TransactionInfoViewItem.ContactItem>().single().contact,
        )
        verify(exactly = 0) {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        }
    }

    @Test
    fun getSwapDetailsSectionItems_completeValues_preservesBothPriceDirections() {
        val items = TransactionViewItemFactoryHelper.getSwapDetailsSectionItems(
            rates = emptyMap(),
            exchangeAddress = BRIDGE_ADDRESS,
            valueOut = tokenValue(code = "OUT", value = BigDecimal.TEN),
            valueIn = tokenValue(code = "IN", value = BigDecimal("20")),
            blockchainType = BlockchainType.BinanceSmartChain,
            providerName = "Provider",
        )

        val prices = items.filterIsInstance<TransactionInfoViewItem.PriceWithToggle>().single()
        assertEquals("OUT = 2 IN", prices.valueOne)
        assertEquals("IN = 0.5 OUT", prices.valueTwo)
    }

    private fun transactionInfoItem(
        record: PendingTransactionRecord,
        offlineStatus: ColoredValue?,
    ) = TransactionInfoItem(
        record = record,
        externalStatus = null,
        lastBlockInfo = null,
        explorerData = emptyList(),
        rates = emptyMap(),
        nftMetadata = emptyMap(),
        hideAmount = false,
        offlineStatus = offlineStatus,
    )

    private fun tokenValue(
        code: String = "COSA",
        value: BigDecimal = BigDecimal.ONE,
    ) = TransactionValue.TokenValue(
        tokenName = "COSA",
        tokenCode = code,
        tokenDecimals = 18,
        value = value,
    )

    private fun pendingRecord(toAddress: String): PendingTransactionRecord {
        val token = Token(
            coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
            blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
            type = TokenType.Derived(TokenType.Derivation.Bip84),
            decimals = 8,
        )
        return PendingTransactionRecord(
            uid = "offline-signed:hash",
            transactionHash = "hash",
            timestamp = 1_000L,
            source = TransactionSource(
                blockchain = token.blockchain,
                account = mockk<Account>(relaxed = true),
                meta = null,
            ),
            token = token,
            amount = BigDecimal.ZERO,
            toAddress = toAddress,
            fromAddress = "",
            expiresAt = Long.MAX_VALUE,
            memo = null,
        )
    }

    private companion object {
        const val BRIDGE_ADDRESS = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436"
    }
}

private class TestCoreApp : CoreApp() {
    override fun localizedContext() = this
    override val isSwapEnabled: Boolean = false
}
