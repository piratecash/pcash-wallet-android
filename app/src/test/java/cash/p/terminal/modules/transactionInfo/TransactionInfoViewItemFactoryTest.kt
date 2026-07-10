package cash.p.terminal.modules.transactionInfo

import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.modules.contacts.ContactsRepository
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
import io.mockk.mockk
import io.mockk.unmockkAll
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

    @Before
    fun setUp() {
        stopKoin()
        CoreApp.instance = RuntimeEnvironment.getApplication() as CoreApp
        startKoin {
            modules(
                module {
                    single<IAppNumberFormatter> { mockk(relaxed = true) }
                    single<ContactsRepository> { mockk(relaxed = true) }
                    single<EvmLabelManager> { mockk(relaxed = true) }
                }
            )
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
            resendEnabled = false,
            blockchainType = BlockchainType.Bitcoin,
        ).getViewItemSections(item)

        val amount = sections.first().first() as TransactionInfoViewItem.Amount
        val recipient = sections.first()[1] as TransactionInfoViewItem.Value
        assertEquals("---", amount.coinValue.value)
        assertEquals("---", amount.fiatValue.value)
        assertEquals("---", recipient.value)
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
}

private class TestCoreApp : CoreApp() {
    override fun localizedContext() = this
    override val isSwapEnabled: Boolean = false
}
