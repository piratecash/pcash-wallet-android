package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CheckAdapterPremiumBalanceUseCaseTest {

    @MockK
    private lateinit var accountManager: IAccountManager

    @MockK
    private lateinit var walletManager: IWalletManager

    @MockK
    private lateinit var adapterManager: IAdapterManager

    private lateinit var useCase: CheckAdapterPremiumBalanceUseCase
    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        useCase =
            CheckAdapterPremiumBalanceUseCaseImpl(accountManager, walletManager, adapterManager)
    }

    @Test
    fun `invoke returns null when account missing`() = runTest {
        every { accountManager.activeAccount } returns null

        assertNull(useCase())
    }

    @Test
    fun `invoke returns null when account not eligible`() = runTest {
        val account = Account(
            id = "watch",
            name = "Watch",
            type = AccountType.EvmAddress("0x0"),
            origin = AccountOrigin.Restored,
            level = 0
        )
        every { accountManager.activeAccount } returns account

        assertNull(useCase())
    }

    @Test
    fun `invoke returns pirate premium when balance meets threshold`() = runTest {
        val account = mnemonicAccount()
        val wallet = pirateWallet(account)
        val balanceAdapter = balanceAdapter(
            available = PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal(),
            state = AdapterState.Synced
        )

        every { accountManager.activeAccount } returns account
        every { walletManager.activeWallets } returns listOf(wallet)
        every { adapterManager.getAdapterForWallet<IBalanceAdapter>(wallet) } returns balanceAdapter
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns receiveAdapter("0x123")

        val result = useCase()

        val premium = assertIs<CheckAdapterPremiumBalanceUseCase.Result.Premium>(result)
        assertEquals(PremiumType.PIRATE, premium.premiumType)
        assertEquals(PremiumConfig.COIN_TYPE_PIRATE, premium.coinType)
        assertEquals("0x123", premium.address)
    }

    @Test
    fun `invoke ignores adapter state when balance sufficient`() = runTest {
        val account = mnemonicAccount()
        val wallet = pirateWallet(account)
        val balanceAdapter = balanceAdapter(
            available = PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal(),
            state = AdapterState.NotSynced(Exception("sync error"))
        )

        every { accountManager.activeAccount } returns account
        every { walletManager.activeWallets } returns listOf(wallet)
        every { adapterManager.getAdapterForWallet<IBalanceAdapter>(wallet) } returns balanceAdapter
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns receiveAdapter("0xabc")

        val result = useCase()

        val premium = assertIs<CheckAdapterPremiumBalanceUseCase.Result.Premium>(result)
        assertEquals("0xabc", premium.address)
    }

    @Test
    fun `invoke returns insufficient pirate when balance below threshold`() = runTest {
        val account = mnemonicAccount()
        val wallet = pirateWallet(account)
        val balanceAdapter = balanceAdapter(
            available = PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal()
                .minus(BigDecimal.ONE),
            state = AdapterState.Synced
        )

        every { accountManager.activeAccount } returns account
        every { walletManager.activeWallets } returns listOf(wallet)
        every { adapterManager.getAdapterForWallet<IBalanceAdapter>(wallet) } returns balanceAdapter
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns receiveAdapter("0x456")

        val result = useCase()

        val insufficient = assertIs<CheckAdapterPremiumBalanceUseCase.Result.Insufficient>(result)
        assertEquals(PremiumConfig.COIN_TYPE_PIRATE, insufficient.coinType)
        assertEquals("0x456", insufficient.address)
    }

    @Test
    fun `invoke returns cosanta premium when balance meets threshold`() = runTest {
        val account = mnemonicAccount()
        val wallet = cosantaWallet(account)
        val balanceAdapter = balanceAdapter(
            available = PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA.toBigDecimal(),
            state = AdapterState.Synced
        )

        every { accountManager.activeAccount } returns account
        every { walletManager.activeWallets } returns listOf(wallet)
        every { adapterManager.getAdapterForWallet<IBalanceAdapter>(wallet) } returns balanceAdapter
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns receiveAdapter("0x789")

        val result = useCase()

        val premium = assertIs<CheckAdapterPremiumBalanceUseCase.Result.Premium>(result)
        assertEquals(PremiumType.COSA, premium.premiumType)
        assertEquals(PremiumConfig.COIN_TYPE_COSANTA, premium.coinType)
        assertEquals("0x789", premium.address)
    }

    @Test
    fun `invoke returns insufficient cosanta when balance below threshold`() = runTest {
        val account = mnemonicAccount()
        val wallet = cosantaWallet(account)
        val balanceAdapter = balanceAdapter(
            available = PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA.toBigDecimal()
                .minus(BigDecimal.ONE),
            state = AdapterState.Synced
        )

        every { accountManager.activeAccount } returns account
        every { walletManager.activeWallets } returns listOf(wallet)
        every { adapterManager.getAdapterForWallet<IBalanceAdapter>(wallet) } returns balanceAdapter
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns receiveAdapter("0x987")

        val result = useCase()

        val insufficient = assertIs<CheckAdapterPremiumBalanceUseCase.Result.Insufficient>(result)
        assertEquals(PremiumConfig.COIN_TYPE_COSANTA, insufficient.coinType)
        assertEquals("0x987", insufficient.address)
    }

    @Test
    fun `invoke returns null when no matching adapters`() = runTest {
        val account = mnemonicAccount()
        val wallet = otherWallet(account)
        val balanceAdapter = balanceAdapter(
            available = BigDecimal.TEN,
            state = AdapterState.Synced
        )

        every { accountManager.activeAccount } returns account
        every { walletManager.activeWallets } returns listOf(wallet)
        every { adapterManager.getAdapterForWallet<IBalanceAdapter>(wallet) } returns balanceAdapter
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns receiveAdapter("0xabc")

        assertNull(useCase())
    }

    private fun mnemonicAccount(): Account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(
            words = List(12) { "abandon" },
            passphrase = ""
        ),
        origin = AccountOrigin.Created,
        level = 1,
        isBackedUp = true
    )

    private fun pirateWallet(account: Account): Wallet =
        walletFactory.create(
            token = token(PremiumConfig.PIRATE_CONTRACT_ADDRESS),
            account = account,
            hardwarePublicKey = null
        )!!

    private fun cosantaWallet(account: Account): Wallet =
        walletFactory.create(
            token = token(PremiumConfig.COSANTA_CONTRACT_ADDRESS),
            account = account,
            hardwarePublicKey = null
        )!!

    private fun otherWallet(account: Account): Wallet =
        walletFactory.create(token = token("0x0"), account = account, hardwarePublicKey = null)!!

    private fun token(contract: String) = Token(
        coin = Coin(uid = contract, name = "Token", code = "TKN"),
        blockchain = Blockchain(BlockchainType.BinanceSmartChain, "BSC", null),
        type = TokenType.Eip20(contract),
        decimals = 18
    )

    private fun balanceAdapter(
        available: BigDecimal,
        state: AdapterState
    ): IBalanceAdapter = mockk {
        every { balanceState } returns state
        every { balanceData } returns BalanceData(available = available)
    }

    private fun receiveAdapter(address: String): IReceiveAdapter = mockk {
        every { receiveAddress } returns address
    }
}
