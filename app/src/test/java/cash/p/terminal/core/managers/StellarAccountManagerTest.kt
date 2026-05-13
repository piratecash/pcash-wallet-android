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
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.room.StellarAsset
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
class StellarAccountManagerTest {

    private val stellarBlockchain = Blockchain(BlockchainType.Stellar, "Stellar", null)

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
    private lateinit var stellarKitManager: StellarKitManager
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
        stellarKitManager = mockk(relaxed = true)
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
    fun handle_assetNotInMarketKit_doesNotSaveEnabledWallet() = runTest {
        val asset = StellarAsset.Asset("SCM", "GA-SCAM-ISSUER")
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()

        val manager = createManager()
        manager.handle(setOf(asset), account)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_assetInMarketKit_savesEnabledWalletWithMarketKitMetadata() = runTest {
        val asset = StellarAsset.Asset("USDC", "GA-CIRCLE-ISSUER")
        val tokenType = TokenType.Asset(asset.code, asset.issuer)
        // Mirror production: TokenType.fromType has no "stellar" branch, so Stellar rows
        // round-trip from CoinStorage as Unsupported("stellar", ...). The matcher must
        // still find them via TokenType.values, which is symmetric across Asset/Unsupported.
        val knownToken = token(
            TokenType.Unsupported("stellar", "${asset.code}-${asset.issuer}"),
            "USD Coin", "USDC", "img-url", 7,
        )
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(knownToken)

        val savedSlot = slot<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(capture(savedSlot)) } returns Unit

        val manager = createManager()
        manager.handle(setOf(asset), account)

        coVerify(exactly = 1) { walletManager.saveEnabledWallets(any()) }
        val saved = savedSlot.captured
        assertEquals(1, saved.size)
        val wallet = saved.single()
        assertEquals(TokenQuery(BlockchainType.Stellar, tokenType).id, wallet.tokenQueryId)
        assertEquals("USD Coin", wallet.coinName)
        assertEquals("USDC", wallet.coinCode)
        assertEquals(7, wallet.coinDecimals)
        assertEquals("img-url", wallet.coinImage)
    }

    @Test
    fun handle_emptyAssets_doesNotCallWalletManager() = runTest {
        val manager = createManager()
        manager.handle(emptySet(), account)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_assetAlreadyEnabled_doesNotSaveDuplicate() = runTest {
        val asset = StellarAsset.Asset("USDC", "GA-CIRCLE-ISSUER")
        val tokenType = TokenType.Asset(asset.code, asset.issuer)
        val existingWallet = mockk<Wallet> {
            every { token } returns token(tokenType, "USD Coin", "USDC", "img-url", 7)
        }
        every { walletManager.activeWallets } returns listOf(existingWallet)

        val manager = createManager()
        manager.handle(setOf(asset), account)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_assetDeletedByUser_doesNotResave() = runTest {
        val asset = StellarAsset.Asset("USDC", "GA-CIRCLE-ISSUER")
        val tokenType = TokenType.Asset(asset.code, asset.issuer)
        val knownToken = token(
            TokenType.Unsupported("stellar", "${asset.code}-${asset.issuer}"),
            "USD Coin", "USDC", "img-url", 7,
        )
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(knownToken)
        coEvery {
            userDeletedWalletManager.isDeletedByUser(account.id, TokenQuery(BlockchainType.Stellar, tokenType).id)
        } returns true

        val manager = createManager()
        manager.handle(setOf(asset), account)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    private fun createManager() = StellarAccountManager(
        accountManager = accountManager,
        walletManager = walletManager,
        stellarKitManager = stellarKitManager,
        tokenAutoEnableManager = tokenAutoEnableManager,
        userDeletedWalletManager = userDeletedWalletManager,
        marketKit = marketKit,
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
        blockchain = stellarBlockchain,
        type = type,
        decimals = decimals,
    )
}
