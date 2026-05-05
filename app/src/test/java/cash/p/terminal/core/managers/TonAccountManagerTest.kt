package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonVerificationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TonAccountManagerTest {

    private val tonBlockchain = Blockchain(BlockchainType.Ton, "TON", null)

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(emptyList(), ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    private lateinit var accountManager: IAccountManager
    private lateinit var walletManager: IWalletManager
    private lateinit var tonKitManager: TonKitManager
    private lateinit var tokenAutoEnableManager: TokenAutoEnableManager
    private lateinit var userDeletedWalletManager: UserDeletedWalletManager
    private lateinit var marketKit: MarketKitWrapper

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
        accountManager = mockk(relaxed = true) {
            every { activeAccount } returns account
        }
        walletManager = mockk(relaxed = true) {
            every { activeWallets } returns emptyList()
        }
        tonKitManager = mockk(relaxed = true)
        tokenAutoEnableManager = mockk(relaxed = true) {
            every { isAutoEnabled(any(), any()) } returns true
        }
        userDeletedWalletManager = mockk(relaxed = true) {
            coEvery { isDeletedByUser(any(), any()) } returns false
        }
        marketKit = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun handle_jettonNotInMarketKit_doesNotSaveEnabledWallet() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "Scam", symbol = "SCM", decimals = 6)
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()

        val manager = createManager()
        manager.handle(setOf(jetton), account)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_jettonInMarketKit_savesEnabledWalletWithMarketKitMetadata() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "OnchainName", symbol = "OCN", decimals = 0)
        val tokenType = TokenType.Jetton(jetton.address.toUserFriendly(true))
        val knownToken = token(tokenType, "Tether USD", "USDT", "img-url", 6)
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(knownToken)

        val savedSlot = slot<List<cash.p.terminal.wallet.entities.EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(capture(savedSlot)) } returns Unit

        val manager = createManager()
        manager.handle(setOf(jetton), account)

        coVerify(exactly = 1) { walletManager.saveEnabledWallets(any()) }
        val saved = savedSlot.captured
        assertEquals(1, saved.size)
        val wallet = saved.single()
        assertEquals(TokenQuery(BlockchainType.Ton, tokenType).id, wallet.tokenQueryId)
        assertEquals("Tether USD", wallet.coinName)
        assertEquals("USDT", wallet.coinCode)
        assertEquals(6, wallet.coinDecimals)
        assertEquals("img-url", wallet.coinImage)
    }

    @Test
    fun handle_emptyJettons_doesNotCallWalletManager() = runTest {
        val manager = createManager()
        manager.handle(emptySet(), account)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_jettonAlreadyEnabled_doesNotSaveDuplicate() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "OnchainName", symbol = "OCN", decimals = 0)
        val tokenType = TokenType.Jetton(jetton.address.toUserFriendly(true))
        val existingWallet = mockk<Wallet> {
            every { token } returns token(tokenType, "Tether USD", "USDT", "img-url", 6)
        }
        every { walletManager.activeWallets } returns listOf(existingWallet)

        val manager = createManager()
        manager.handle(setOf(jetton), account)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_jettonDeletedByUser_doesNotResave() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "OnchainName", symbol = "OCN", decimals = 0)
        val tokenType = TokenType.Jetton(jetton.address.toUserFriendly(true))
        val knownToken = token(tokenType, "Tether USD", "USDT", "img-url", 6)
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(knownToken)
        coEvery {
            userDeletedWalletManager.isDeletedByUser(account.id, TokenQuery(BlockchainType.Ton, tokenType).id)
        } returns true

        val manager = createManager()
        manager.handle(setOf(jetton), account)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    private fun createManager() = TonAccountManager(
        accountManager = accountManager,
        walletManager = walletManager,
        tonKitManager = tonKitManager,
        tokenAutoEnableManager = tokenAutoEnableManager,
        userDeletedWalletManager = userDeletedWalletManager,
        marketKit = marketKit,
    )

    private fun mockJetton(
        addressBase64: String,
        name: String,
        symbol: String,
        decimals: Int,
        verification: JettonVerificationType = JettonVerificationType.NONE,
    ): Jetton = Jetton(
        address = Address.parse(addressBase64),
        name = name,
        symbol = symbol,
        decimals = decimals,
        image = null,
        verification = verification,
    )

    private fun token(
        type: TokenType,
        coinName: String,
        coinCode: String,
        coinImage: String?,
        decimals: Int,
    ) = Token(
        coin = Coin(
            uid = "$coinCode-uid",
            name = coinName,
            code = coinCode,
            image = coinImage,
        ),
        blockchain = tonBlockchain,
        type = type,
        decimals = decimals,
    )

    companion object {
        // Valid base64url TON addresses for tests.
        private const val TEST_ADDRESS_1 =
            "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    }
}
