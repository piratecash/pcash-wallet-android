package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.binance.data.TokenBalance
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.data.repository.PremiumUserRepository
import cash.p.terminal.premium.domain.usecase.CheckAdapterPremiumBalanceUseCase.Result.Insufficient
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.UserManager
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.premium.data.model.PremiumUser
import cash.p.terminal.premium.domain.TestDispatcherProvider
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class CheckPremiumUseCaseTest {

    @MockK
    private lateinit var premiumUserRepository: PremiumUserRepository

    @MockK
    private lateinit var binanceApi: BinanceApi

    @MockK
    private lateinit var piratePlaceRepository: PiratePlaceRepository

    @MockK
    private lateinit var accountManager: IAccountManager

    @MockK
    private lateinit var checkAdapterPremiumBalanceUseCase: CheckAdapterPremiumBalanceUseCase

    @MockK
    private lateinit var checkTrialPremiumUseCase: CheckTrialPremiumUseCase

    @MockK
    private lateinit var activateTrialPremiumUseCase: ActivateTrialPremiumUseCase

    @MockK
    private lateinit var getBnbAddressUseCase: GetBnbAddressUseCase

    @MockK
    private lateinit var userManager: UserManager

    private lateinit var useCase: CheckPremiumUseCaseImpl

    private val dispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = TestDispatcherProvider(dispatcher)
    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    @Test
    fun `update falls back to remote when adapter insufficient`() = runTest(dispatcher) {
        val account = mnemonicAccount()
        val pirateWallet = wallet(account, contract = PremiumConfig.PIRATE_CONTRACT_ADDRESS)

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { activateTrialPremiumUseCase.activateTrialPremium(any()) } returns TrialPremiumResult.DemoNotFound

        val insufficient = Insufficient(
            account = account,
            wallet = pirateWallet,
            address = "0xpirate",
            coinType = PremiumConfig.COIN_TYPE_PIRATE
        )
        coEvery { checkAdapterPremiumBalanceUseCase.invoke() } returns insufficient

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account, any()) } returns "0xcosanta"
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcosanta"
        coEvery { getBnbAddressUseCase.saveAddress(any(), any()) } returns Unit
        coEvery { getBnbAddressUseCase.deleteBnbAddress(any()) } returns Unit

        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xcosanta")
        } returns TokenBalance(BigDecimal.ZERO)
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.COSANTA_CONTRACT_ADDRESS, "0xcosanta")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA.toBigDecimal() + BigDecimal.ONE)

        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected fallback")

        useCase = CheckPremiumUseCaseImpl(
            premiumUserRepository = premiumUserRepository,
            binanceApi = binanceApi,
            piratePlaceRepository = piratePlaceRepository,
            accountManager = accountManager,
            checkAdapterPremiumBalanceUseCase = checkAdapterPremiumBalanceUseCase,
            checkTrialPremiumUseCase = checkTrialPremiumUseCase,
            activateTrialPremiumUseCase = activateTrialPremiumUseCase,
            getBnbAddressUseCase = getBnbAddressUseCase,
            userManager = userManager,
            dispatcherProvider = testDispatcherProvider
        )

        advanceUntilIdle()

        val result = useCase.update()

        assertEquals(PremiumType.COSA, result)
        assertEquals(PremiumType.COSA, useCase.getPremiumType())
    }

    @Test
    fun `getPremiumType prefers cached premium`() = runTest(dispatcher) {
        val account = mnemonicAccount()

        stubActiveAccount(account)

        val cachedUser = PremiumUser(
            level = 1,
            accountId = account.id,
            address = "0xcached",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.PIRATE
        )
        coEvery { premiumUserRepository.getByLevel(1) } returns cachedUser
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { activateTrialPremiumUseCase.activateTrialPremium(any()) } returns TrialPremiumResult.DemoNotFound

        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account, any()) } returns "0xcached"
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcached"
        coEvery { getBnbAddressUseCase.saveAddress(any(), any()) } returns Unit
        coEvery { getBnbAddressUseCase.deleteBnbAddress(any()) } returns Unit

        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected pirate call")

        useCase = createUseCase()

        advanceUntilIdle()

        val result = useCase.getPremiumType()

        assertEquals(PremiumType.PIRATE, result)
        verify(exactly = 0) { checkAdapterPremiumBalanceUseCase.invoke() }
    }

    @Test
    fun `getPremiumType fetches adapter premium when cache empty`() = runTest(dispatcher) {
        val account = mnemonicAccount()
        val cosantaWallet = wallet(account, PremiumConfig.COSANTA_CONTRACT_ADDRESS)

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { activateTrialPremiumUseCase.activateTrialPremium(any()) } returns TrialPremiumResult.DemoNotFound

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { activateTrialPremiumUseCase.activateTrialPremium(any()) } returns TrialPremiumResult.DemoNotFound

        val premiumResult = CheckAdapterPremiumBalanceUseCase.Result.Premium(
            account = account,
            wallet = cosantaWallet,
            address = "0xcosanta",
            coinType = PremiumConfig.COIN_TYPE_COSANTA,
            premiumType = PremiumType.COSA
        )
        every { checkAdapterPremiumBalanceUseCase.invoke() } returnsMany listOf(null, premiumResult, premiumResult)

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account, any()) } returns "0xcached"
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcached"
        coEvery { getBnbAddressUseCase.saveAddress(any(), any()) } returns Unit
        coEvery { getBnbAddressUseCase.deleteBnbAddress(any()) } returns Unit

        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected pirate call")

        useCase = createUseCase()

        advanceUntilIdle()

        val premium = useCase.getPremiumType()
        advanceUntilIdle()
        val cached = useCase.getPremiumType()

        assertEquals(PremiumType.COSA, premium)
        assertEquals(PremiumType.COSA, cached)

        verify(exactly = 4) { checkAdapterPremiumBalanceUseCase.invoke() }
        coVerify { premiumUserRepository.insert(match { it.isPremium == PremiumType.COSA }) }
    }

    @Test
    fun `getPremiumType remains none when adapter reports insufficient`() = runTest(dispatcher) {
        val account = mnemonicAccount()
        val pirateWallet = wallet(account, PremiumConfig.PIRATE_CONTRACT_ADDRESS)

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { activateTrialPremiumUseCase.activateTrialPremium(any()) } returns TrialPremiumResult.DemoNotFound

        val insufficient = Insufficient(
            account = account,
            wallet = pirateWallet,
            address = "0xpirate",
            coinType = PremiumConfig.COIN_TYPE_PIRATE
        )
        every { checkAdapterPremiumBalanceUseCase.invoke() } returnsMany listOf(null, insufficient)

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account, any()) } returns "0xcached"
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcached"
        coEvery { getBnbAddressUseCase.saveAddress(any(), any()) } returns Unit
        coEvery { getBnbAddressUseCase.deleteBnbAddress(any()) } returns Unit

        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected pirate call")

        useCase = createUseCase()

        advanceUntilIdle()

        val result = useCase.getPremiumType()

        assertEquals(PremiumType.NONE, result)
        verify(exactly = 5) { checkAdapterPremiumBalanceUseCase.invoke() }
    }

    @Test
    fun `getPremiumType remains none when adapter has no data`() = runTest(dispatcher) {
        val account = mnemonicAccount()

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { activateTrialPremiumUseCase.activateTrialPremium(any()) } returns TrialPremiumResult.DemoNotFound

        every { checkAdapterPremiumBalanceUseCase.invoke() } returnsMany listOf(null, null)

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account, any()) } returns "0xcached"
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcached"
        coEvery { getBnbAddressUseCase.saveAddress(any(), any()) } returns Unit
        coEvery { getBnbAddressUseCase.deleteBnbAddress(any()) } returns Unit

        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected pirate call")

        useCase = createUseCase()

        advanceUntilIdle()

        val result = useCase.getPremiumType()

        assertEquals(PremiumType.NONE, result)
        verify(exactly = 5) { checkAdapterPremiumBalanceUseCase.invoke() }
    }

    private fun createUseCase(): CheckPremiumUseCaseImpl = CheckPremiumUseCaseImpl(
        premiumUserRepository = premiumUserRepository,
        binanceApi = binanceApi,
        piratePlaceRepository = piratePlaceRepository,
        accountManager = accountManager,
        checkAdapterPremiumBalanceUseCase = checkAdapterPremiumBalanceUseCase,
        checkTrialPremiumUseCase = checkTrialPremiumUseCase,
        activateTrialPremiumUseCase = activateTrialPremiumUseCase,
        getBnbAddressUseCase = getBnbAddressUseCase,
        userManager = userManager,
        dispatcherProvider = testDispatcherProvider
    )

    private fun stubActiveAccount(account: Account, level: Int = 1) {
        val levelFlow = MutableStateFlow(level)
        every { userManager.currentUserLevelFlow } returns levelFlow
        val accountsFlow = MutableStateFlow(listOf(account))
        every { accountManager.accountsFlow } returns accountsFlow
        every { accountManager.accounts } returns listOf(account)
        every { accountManager.activeAccount } returns account
        every { accountManager.account(account.id) } returns account
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

    private fun wallet(account: Account, contract: String): Wallet = walletFactory.create(
        token = Token(
            coin = Coin(uid = contract, name = "Token", code = "TKN"),
            blockchain = Blockchain(BlockchainType.BinanceSmartChain, "BSC", null),
            type = TokenType.Eip20(contract),
            decimals = 18
        ),
        account = account,
        hardwarePublicKey = null
    )!!
}
