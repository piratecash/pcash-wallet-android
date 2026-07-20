package cash.p.terminal.premium.domain.usecase

import android.os.SystemClock
import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.binance.data.TokenBalance
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.data.dao.AccountPremiumCacheDao
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.model.AccountPremiumCacheEntity
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
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class CheckPremiumUseCaseTest {

    @MockK
    private lateinit var premiumUserRepository: PremiumUserRepository

    @MockK
    private lateinit var demoPremiumUserDao: DemoPremiumUserDao

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

    @MockK
    private lateinit var accountPremiumCacheDao: AccountPremiumCacheDao

    private lateinit var useCase: CheckPremiumUseCaseImpl

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val testDispatcherProvider = TestDispatcherProvider(dispatcher, testScope)
    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtimeNanos() } returns 0L
        // Default: empty persisted cache, no deleted accounts. Individual tests override as needed.
        coEvery { accountPremiumCacheDao.getAll() } returns emptyList()
        coEvery { accountPremiumCacheDao.upsert(any()) } returns Unit
        coEvery { accountPremiumCacheDao.deleteByAccountIds(any()) } returns Unit
        every { accountManager.getDeletedAccountIds() } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `update falls back to remote when adapter insufficient`() = runTest(dispatcher) {
        val account = mnemonicAccount()
        val pirateWallet = wallet(account, contract = PremiumConfig.PIRATE_CONTRACT_ADDRESS)

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.getByLevel(0) } returns null
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

        coEvery { demoPremiumUserDao.hasActiveTrialPremium() } returns false

        useCase = CheckPremiumUseCaseImpl(
            premiumUserRepository = premiumUserRepository,
            demoPremiumUserDao = demoPremiumUserDao,
            binanceApi = binanceApi,
            piratePlaceRepository = piratePlaceRepository,
            accountManager = accountManager,
            checkAdapterPremiumBalanceUseCase = checkAdapterPremiumBalanceUseCase,
            checkTrialPremiumUseCase = checkTrialPremiumUseCase,
            activateTrialPremiumUseCase = activateTrialPremiumUseCase,
            getBnbAddressUseCase = getBnbAddressUseCase,
            userManager = userManager,
            accountPremiumCacheDao = accountPremiumCacheDao,
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
        coEvery { premiumUserRepository.getByLevel(0) } returns null
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
        // Adapter is called for parent level (0) which has no cache, but current level (1) uses cache
    }

    @Test
    fun `getPremiumType fetches adapter premium when cache empty`() = runTest(dispatcher) {
        val account = mnemonicAccount()
        val cosantaWallet = wallet(account, PremiumConfig.COSANTA_CONTRACT_ADDRESS)

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.getByLevel(0) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { activateTrialPremiumUseCase.activateTrialPremium(any()) } returns TrialPremiumResult.DemoNotFound

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.getByLevel(0) } returns null
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

        verify(atLeast = 2) { checkAdapterPremiumBalanceUseCase.invoke() }
        coVerify { premiumUserRepository.insert(match { it.isPremium == PremiumType.COSA }) }
    }

    @Test
    fun `getPremiumType remains none when adapter reports insufficient`() = runTest(dispatcher) {
        val account = mnemonicAccount()
        val pirateWallet = wallet(account, PremiumConfig.PIRATE_CONTRACT_ADDRESS)

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.getByLevel(0) } returns null
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
        verify(atLeast = 2) { checkAdapterPremiumBalanceUseCase.invoke() }
    }

    @Test
    fun `getPremiumType remains none when adapter has no data`() = runTest(dispatcher) {
        val account = mnemonicAccount()

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.getByLevel(0) } returns null
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
        verify(atLeast = 2) { checkAdapterPremiumBalanceUseCase.invoke() }
    }

    // ==================== getParentPremiumType tests ====================

    @Test
    fun `getParentPremiumType returns same as getPremiumType when at level 0`() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "main-account", level = 0)

        stubActiveAccount(account, level = 0)

        val cachedUser = PremiumUser(
            level = 0,
            accountId = account.id,
            address = "0xcached",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.PIRATE
        )
        coEvery { premiumUserRepository.getByLevel(0) } returns cachedUser
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcached"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        useCase = createUseCase()
        advanceUntilIdle()

        // At level 0, parent level equals current level
        assertEquals(PremiumType.PIRATE, useCase.getPremiumType())
        assertEquals(PremiumType.PIRATE, useCase.getParentPremiumType(userLevel = 0))
    }

    @Test
    fun `getParentPremiumType returns parent cached premium when in duress mode`() = runTest(dispatcher) {
        val mainAccount = mnemonicAccount(id = "main-account", level = 0)
        val duressAccount = mnemonicAccount(id = "duress-account", level = 1)

        stubTwoLevelAccounts(mainAccount, duressAccount, currentLevel = 1)

        // Parent (level 0) has PIRATE premium
        val parentCachedUser = PremiumUser(
            level = 0,
            accountId = mainAccount.id,
            address = "0xparent",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.PIRATE
        )
        // Current (level 1) has no premium
        coEvery { premiumUserRepository.getByLevel(0) } returns parentCachedUser
        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(any()) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xaddress"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.NONE, useCase.getPremiumType())
        assertEquals(PremiumType.PIRATE, useCase.getParentPremiumType(0))
    }

    @Test
    fun `getParentPremiumType returns trial when parent has trial premium`() = runTest(dispatcher) {
        val mainAccount = mnemonicAccount(id = "main-account", level = 0)
        val duressAccount = mnemonicAccount(id = "duress-account", level = 1)

        stubTwoLevelAccounts(mainAccount, duressAccount, currentLevel = 1)

        // Parent (level 0) stored in repository - needed for _levelAccountCache
        val parentCachedUser = PremiumUser(
            level = 0,
            accountId = mainAccount.id,
            address = "0xparent",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.NONE
        )
        coEvery { premiumUserRepository.getByLevel(0) } returns parentCachedUser
        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        // Parent account has trial premium active
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(mainAccount) } returns TrialPremiumResult.DemoActive(daysLeft = 7)
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(duressAccount) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xaddress"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.TRIAL, useCase.getParentPremiumType(0))
    }

    @Test
    fun `getParentPremiumType returns different type than current level`() = runTest(dispatcher) {
        val mainAccount = mnemonicAccount(id = "main-account", level = 0)
        val duressAccount = mnemonicAccount(id = "duress-account", level = 1)

        stubTwoLevelAccounts(mainAccount, duressAccount, currentLevel = 1)

        // Parent (level 0) has COSA premium
        val parentCachedUser = PremiumUser(
            level = 0,
            accountId = mainAccount.id,
            address = "0xparent",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_COSANTA,
            isPremium = PremiumType.COSA
        )
        // Current (level 1) has PIRATE premium
        val currentCachedUser = PremiumUser(
            level = 1,
            accountId = duressAccount.id,
            address = "0xcurrent",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.PIRATE
        )
        coEvery { premiumUserRepository.getByLevel(0) } returns parentCachedUser
        coEvery { premiumUserRepository.getByLevel(1) } returns currentCachedUser
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(any()) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xaddress"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.PIRATE, useCase.getPremiumType())
        assertEquals(PremiumType.COSA, useCase.getParentPremiumType(0))
    }

    // ==================== update() tests for parent level ====================

    @Test
    fun `update updates both current and parent levels`() = runTest(dispatcher) {
        val mainAccount = mnemonicAccount(id = "main-account", level = 0)
        val duressAccount = mnemonicAccount(id = "duress-account", level = 1)

        stubTwoLevelAccounts(mainAccount, duressAccount, currentLevel = 1)

        coEvery { premiumUserRepository.getByLevel(0) } returns null
        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(any()) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xaddress"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        useCase = createUseCase()
        advanceUntilIdle()

        useCase.update()

        // Verify both levels were queried
        coVerify { premiumUserRepository.getByLevel(1) }
        coVerify { premiumUserRepository.getByLevel(0) }
    }

    // ==================== isPremiumWithParentInCache tests ====================

    @Test
    fun `isPremiumWithParentInCache returns true when token premium cached for current level`() = runTest(dispatcher) {
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
        coEvery { premiumUserRepository.getByLevels(listOf(1, 0)) } returns listOf(cachedUser)
        coEvery { premiumUserRepository.getByLevel(any()) } returns cachedUser
        coEvery { premiumUserRepository.insert(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(any()) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xcached"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        useCase = createUseCase()
        advanceUntilIdle()

        val result = useCase.getParentPremiumType(userLevel = 0)

        assertEquals(PremiumType.PIRATE, result)
    }

    @Test
    fun `isPremiumWithParentInCache returns true when token premium cached for parent level`() = runTest(dispatcher) {
        val mainAccount = mnemonicAccount(id = "main-account", level = 0)
        val duressAccount = mnemonicAccount(id = "duress-account", level = 1)

        stubTwoLevelAccounts(mainAccount, duressAccount, currentLevel = 1)

        // Parent (level 0) has PIRATE premium, current (level 1) has none
        val parentCachedUser = PremiumUser(
            level = 0,
            accountId = mainAccount.id,
            address = "0xparent",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.PIRATE
        )
        coEvery { premiumUserRepository.getByLevels(listOf(1, 0)) } returns listOf(parentCachedUser)
        coEvery { premiumUserRepository.getByLevel(0) } returns parentCachedUser
        coEvery { premiumUserRepository.getByLevel(1) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(any()) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xaddress"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        useCase = createUseCase()
        advanceUntilIdle()

        val result = useCase.getParentPremiumType(userLevel = 0)

        assertEquals(PremiumType.PIRATE, result)
    }

    @Test
    fun `isPremiumWithParentInCache returns true when trial premium is active`() = runTest(dispatcher) {
        val account = mnemonicAccount()

        stubActiveAccount(account)

        // No token premium cached
        coEvery { premiumUserRepository.getByLevels(listOf(0, 0)) } returns emptyList()
        coEvery { premiumUserRepository.getByLevel(any()) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(any()) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xaddress"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        // Trial premium IS active in the database
        coEvery { demoPremiumUserDao.hasActiveTrialPremium() } returns true

        useCase = CheckPremiumUseCaseImpl(
            premiumUserRepository = premiumUserRepository,
            demoPremiumUserDao = demoPremiumUserDao,
            binanceApi = binanceApi,
            piratePlaceRepository = piratePlaceRepository,
            accountManager = accountManager,
            checkAdapterPremiumBalanceUseCase = checkAdapterPremiumBalanceUseCase,
            checkTrialPremiumUseCase = checkTrialPremiumUseCase,
            activateTrialPremiumUseCase = activateTrialPremiumUseCase,
            getBnbAddressUseCase = getBnbAddressUseCase,
            userManager = userManager,
            accountPremiumCacheDao = accountPremiumCacheDao,
            dispatcherProvider = testDispatcherProvider
        )
        advanceUntilIdle()

        val result = useCase.isPremiumWithParentInCache(userLevel = 0)

        assertTrue(result)
    }

    @Test
    fun `isPremiumWithParentInCache returns false when no premium cached`() = runTest(dispatcher) {
        val account = mnemonicAccount()

        stubActiveAccount(account)

        // No token premium cached
        coEvery { premiumUserRepository.getByLevels(listOf(1, 0)) } returns emptyList()
        coEvery { premiumUserRepository.getByLevel(any()) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(any()) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xaddress"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        // No trial premium either
        coEvery { demoPremiumUserDao.hasActiveTrialPremium() } returns false

        useCase = createUseCase()
        advanceUntilIdle()

        val result = useCase.getParentPremiumType(userLevel = 0)

        assertEquals(PremiumType.NONE, result)
    }

    @Test
    fun `isPremiumWithParentInCache ignores NONE premium type in cache`() = runTest(dispatcher) {
        val account = mnemonicAccount()

        stubActiveAccount(account)

        // Cached user has NONE premium
        val cachedUser = PremiumUser(
            level = 1,
            accountId = account.id,
            address = "0xcached",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.NONE
        )
        coEvery { premiumUserRepository.getByLevels(listOf(1, 0)) } returns listOf(cachedUser)
        coEvery { premiumUserRepository.getByLevel(any()) } returns cachedUser
        coEvery { premiumUserRepository.insert(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(any()) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(any<Account>()) } returns "0xcached"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        // No trial premium
        coEvery { demoPremiumUserDao.hasActiveTrialPremium() } returns false

        useCase = createUseCase()
        advanceUntilIdle()

        val result = useCase.getParentPremiumType(userLevel = 0)

        assertEquals(PremiumType.NONE, result)
    }

    @Test
    fun update_offlineWithStaleCachedPremium_returnsCachedPremiumNotNone() = runTest(dispatcher) {
        val account = mnemonicAccount()

        stubActiveAccount(account)

        // Stale lastCheckDate forces the full balance-check path (skips checkCachedPremiumStatus).
        val staleCachedUser = PremiumUser(
            level = 1,
            accountId = account.id,
            address = "0xcached",
            lastCheckDate = System.currentTimeMillis() - PremiumConfig.PREMIUM_CHECK_INTERVAL - 1000,
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.PIRATE
        )
        coEvery { premiumUserRepository.getByLevel(1) } returns staleCachedUser
        coEvery { premiumUserRepository.getByLevel(0) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account, any()) } returns "0xcached"
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcached"

        // Simulate offline: both balance providers fail to deliver a value.
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns null
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Offline")

        coEvery { demoPremiumUserDao.hasActiveTrialPremium() } returns false

        useCase = createUseCase()
        advanceUntilIdle()

        val result = useCase.update()

        // Stale cache + unreachable balance providers must preserve the last confirmed
        // premium rather than collapse to NONE. Otherwise downstream guards (calculator
        // mode disable) would treat transient network failures as entitlement loss.
        assertEquals(PremiumType.PIRATE, result)
    }

    @Test
    fun update_offlineWithActiveTrialCache_returnsTrialNotNone() = runTest(dispatcher) {
        val account = mnemonicAccount()

        stubActiveAccount(account)

        coEvery { premiumUserRepository.getByLevel(any()) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        // Trial use case keeps returning DemoActive offline thanks to its own Room cache.
        coEvery {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(account)
        } returns TrialPremiumResult.DemoActive(daysLeft = 3)
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account, any()) } returns "0xaddress"
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xaddress"

        coEvery { binanceApi.getTokenBalance(any(), any()) } returns null
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Offline")

        coEvery { demoPremiumUserDao.hasActiveTrialPremium() } returns true

        useCase = createUseCase()
        advanceUntilIdle()

        val result = useCase.update()

        // Trial users must remain TRIAL when balance providers are unreachable, not be
        // demoted to NONE.
        assertEquals(PremiumType.TRIAL, result)
    }

    @Test
    fun `update does not update parent level when at level 0`() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "main-account", level = 0)

        stubActiveAccount(account, level = 0)

        coEvery { premiumUserRepository.getByLevel(0) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null

        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xaddress"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        useCase = createUseCase()
        advanceUntilIdle()

        useCase.update()

        // At level 0, parent == current, so getByLevel(0) called but not getByLevel(-1)
        coVerify(atLeast = 1) { premiumUserRepository.getByLevel(0) }
        coVerify(exactly = 0) { premiumUserRepository.getByLevel(-1) }
    }

    @Test
    fun checkPremiumByBalanceForAccount_coldCacheTrialActive_returnsTrial() = runTest(dispatcher) {
        val account = mnemonicAccount()
        // DEFAULT level makes the constructor's background update() short-circuit.
        stubActiveAccount(account, level = UserManager.DEFAULT_USER_LEVEL)
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xaddr"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(account)
        } returns TrialPremiumResult.DemoActive(daysLeft = 3)

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.TRIAL, useCase.checkPremiumByBalanceForAccount(account))
    }

    @Test
    fun getPremiumTypesForAccounts_oneAccountThrows_isolatesFailureAsNone() = runTest(dispatcher) {
        val good = mnemonicAccount(id = "good")
        val bad = mnemonicAccount(id = "bad")
        stubActiveAccount(good, level = UserManager.DEFAULT_USER_LEVEL)
        every { accountManager.accounts } returns listOf(good, bad)
        every { accountManager.account("good") } returns good
        every { accountManager.account("bad") } returns bad

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(good) } returns TrialPremiumResult.NeedPremium
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(bad) } returns TrialPremiumResult.NeedPremium
        coEvery { getBnbAddressUseCase.getAddress(good) } returns "0xgood"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xgood")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)
        coEvery { getBnbAddressUseCase.getAddress(bad) } throws IllegalStateException("boom")

        useCase = createUseCase()
        advanceUntilIdle()

        val result = useCase.getPremiumTypesForAccounts(listOf(good, bad))

        assertEquals(PremiumType.PIRATE, result["good"])
        assertEquals(PremiumType.NONE, result["bad"])
    }

    @Test
    fun checkPremiumByBalanceForAccount_trialAndTokenBothActive_prefersTrial() = runTest(dispatcher) {
        val account = mnemonicAccount()
        stubActiveAccount(account, level = UserManager.DEFAULT_USER_LEVEL)
        // Token balance also qualifies, but an active trial must take precedence regardless of order.
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xaddr"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xaddr")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)
        coEvery {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(account)
        } returns TrialPremiumResult.DemoActive(daysLeft = 5)

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.TRIAL, useCase.checkPremiumByBalanceForAccount(account))
    }

    @Test
    fun getPremiumTypesForAccounts_secondCallWithinTtl_usesCache() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "cache1")
        // Empty account cache so the init warm-up does not pre-populate; the account is passed explicitly.
        stubNoAccounts()
        every { accountManager.account("cache1") } returns account
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcache1"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xcache1")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.PIRATE, useCase.getPremiumTypesForAccounts(listOf(account))["cache1"])
        // Second call within the TTL is served from cache — no further live address resolution.
        assertEquals(PremiumType.PIRATE, useCase.getPremiumTypesForAccounts(listOf(account))["cache1"])

        coVerify(exactly = 1) { getBnbAddressUseCase.getAddress(account) }
    }

    @Test
    fun getPremiumTypesForAccounts_slowAccount_timesOutToNoneWithoutBlockingOthers() = runTest(dispatcher) {
        val slow = mnemonicAccount(id = "slow")
        val fast = mnemonicAccount(id = "fast")
        stubNoAccounts()
        every { accountManager.account("slow") } returns slow
        every { accountManager.account("fast") } returns fast

        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(slow) } returns TrialPremiumResult.NeedPremium
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(fast) } returns TrialPremiumResult.NeedPremium
        // The slow account never returns within the per-account timeout.
        coEvery { getBnbAddressUseCase.getAddress(slow) } coAnswers {
            delay(Long.MAX_VALUE / 2)
            "0xslow"
        }
        coEvery { getBnbAddressUseCase.getAddress(fast) } returns "0xfast"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xfast")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)

        useCase = createUseCase()
        advanceUntilIdle()

        val result = useCase.getPremiumTypesForAccounts(listOf(slow, fast))

        // The slow account is capped to NONE by the timeout; the fast one still resolves.
        assertEquals(PremiumType.NONE, result["slow"])
        assertEquals(PremiumType.PIRATE, result["fast"])
    }

    @Test
    fun getPremiumTypesForAccounts_providerOutageThenRecovery_doesNotCacheNone() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "out1")
        stubNoAccounts()
        every { accountManager.account("out1") } returns account
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xout1"
        // Outage: neither provider returns a balance -> indeterminate NONE, must NOT be cached.
        coEvery { binanceApi.getTokenBalance(any(), "0xout1") } returns null
        coEvery { piratePlaceRepository.getInvestmentData(any(), "0xout1") } throws IllegalStateException("outage")

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.NONE, useCase.getPremiumTypesForAccounts(listOf(account))["out1"])

        // Providers recover: the second call must re-check (not serve a cached NONE) and see PIRATE.
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xout1")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)

        assertEquals(PremiumType.PIRATE, useCase.getPremiumTypesForAccounts(listOf(account))["out1"])
    }

    @Test
    fun premiumTypesFlow_trialActivatedAfterCaching_showsTrial() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "tr1")
        stubNoAccounts()
        every { accountManager.account("tr1") } returns account
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xtr1"
        coEvery { binanceApi.getTokenBalance(any(), "0xtr1") } returns TokenBalance(BigDecimal.ZERO)

        useCase = createUseCase()
        advanceUntilIdle()

        // Token-only warm-up caches a definitive NONE for the account.
        useCase.getPremiumTypesForAccounts(listOf(account))
        advanceUntilIdle()
        assertEquals(PremiumType.NONE, useCase.premiumTypesFlow.value["tr1"])

        // Activating a trial is layered over the token cache on the display flow.
        coEvery {
            activateTrialPremiumUseCase.activateTrialPremium("tr1")
        } returns TrialPremiumResult.DemoActive(daysLeft = 3)
        useCase.activateTrialPremium("tr1")
        advanceUntilIdle()

        assertEquals(PremiumType.TRIAL, useCase.premiumTypesFlow.value["tr1"])
    }

    @Test
    fun getPremiumTypesForAccounts_callerCancelled_cancelsScan() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "cx1")
        stubNoAccounts()
        every { accountManager.account("cx1") } returns account
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        // Address resolution suspends; the caller is cancelled while the scan is in flight.
        coEvery { getBnbAddressUseCase.getAddress(account) } coAnswers {
            delay(1000)
            "0xcx1"
        }
        coEvery { binanceApi.getTokenBalance(any(), "0xcx1") } returns TokenBalance(BigDecimal.ZERO)

        useCase = createUseCase()
        advanceUntilIdle()

        val job = launch { useCase.getPremiumTypesForAccounts(listOf(account)) }
        runCurrent() // the scan starts and suspends inside the address resolution
        job.cancel()
        advanceUntilIdle()

        // The scan runs in the caller's coroutine, so cancelling the caller cancels it — no detached work
        // continues, and the balance check downstream of the cancelled address resolution is never reached.
        coVerify(exactly = 0) { binanceApi.getTokenBalance(any(), "0xcx1") }
    }

    @Test
    fun premiumTypesFlow_trialExpired_fallsBackToToken() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "tx1", level = 1)
        stubActiveAccount(account, level = 1)
        coEvery { premiumUserRepository.getByLevel(any()) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xtx1"
        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { binanceApi.getTokenBalance(any(), "0xtx1") } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), "0xtx1") } throws IllegalStateException("Unexpected")

        // Trial is active -> the background update records it as the single trial source.
        coEvery {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(account)
        } returns TrialPremiumResult.DemoActive(daysLeft = 2)

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.TRIAL, useCase.premiumTypesFlow.value["tx1"])

        // Trial expires: the next background update drops it from the single trial source, so the display
        // flow must fall back to the token/NONE state instead of staying pinned as TRIAL forever.
        coEvery {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(account)
        } returns TrialPremiumResult.NeedPremium
        useCase.update()
        advanceUntilIdle()

        assertEquals(PremiumType.NONE, useCase.premiumTypesFlow.value["tx1"])
    }

    @Test
    fun getPremiumType_tokenPremiumWithExpiredTrial_dropsStaleTrial() = runTest(dispatcher) {
        val account = mnemonicAccount(level = 1)
        stubActiveAccount(account, level = 1)

        // Fresh cached PIRATE token premium: `checkCachedPremiumStatus` returns PIRATE, so the level update
        // takes the "has token premium" branch that USED to skip the trial refresh.
        val cachedUser = PremiumUser(
            level = 1,
            accountId = account.id,
            address = "0xcached",
            lastCheckDate = System.currentTimeMillis(),
            coinType = PremiumConfig.COIN_TYPE_PIRATE,
            isPremium = PremiumType.PIRATE
        )
        coEvery { premiumUserRepository.getByLevel(1) } returns cachedUser
        coEvery { premiumUserRepository.getByLevel(0) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null
        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xcached"
        coEvery { binanceApi.getTokenBalance(any(), any()) } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), any()) } throws IllegalStateException("Unexpected")

        // Trial is active alongside the token premium; trial takes display precedence.
        coEvery {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(account)
        } returns TrialPremiumResult.DemoActive(daysLeft = 2)

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.TRIAL, useCase.getPremiumType())

        // Trial expires: even though the account keeps token premium, the unconditional per-update trial
        // refresh must drop the stale TRIAL so the real token type shows instead of a lingering TRIAL.
        coEvery {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(account)
        } returns TrialPremiumResult.NeedPremium
        useCase.update()
        advanceUntilIdle()

        assertEquals(PremiumType.PIRATE, useCase.getPremiumType())
    }

    @Test
    fun getPremiumTypesForAccounts_tokenOnlyScan_cachesDespiteTrialError() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "de1")
        stubNoAccounts()
        every { accountManager.account("de1") } returns account
        // Trial resolution would error, but the warm-up is token-only (checkTrial = false): it never checks
        // the trial, so a qualifying PIRATE balance is cached as usual and re-used on the next call.
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.DemoError()
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xde1"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xde1")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.PIRATE, useCase.getPremiumTypesForAccounts(listOf(account))["de1"])
        // Cached: the second call is served from the fresh token cache without a live address resolution.
        useCase.getPremiumTypesForAccounts(listOf(account))
        coVerify(exactly = 1) { getBnbAddressUseCase.getAddress(account) }
    }

    @Test
    fun premiumTypesFlow_trialActivatedDuringScan_notMaskedByNone() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "ta1")
        stubNoAccounts()
        every { accountManager.account("ta1") } returns account
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xta1"
        // Suspend the balance check so a trial can be activated mid-scan; the scan would resolve to NONE.
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xta1")
        } coAnswers {
            delay(1000)
            TokenBalance(BigDecimal.ZERO)
        }
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.COSANTA_CONTRACT_ADDRESS, "0xta1")
        } returns TokenBalance(BigDecimal.ZERO)
        coEvery {
            activateTrialPremiumUseCase.activateTrialPremium("ta1")
        } returns TrialPremiumResult.DemoActive(daysLeft = 3)

        useCase = createUseCase()
        advanceUntilIdle()

        val scan = async { useCase.getPremiumTypesForAccounts(listOf(account)) }
        runCurrent() // let the scan start and suspend at the balance check
        useCase.activateTrialPremium("ta1") // records the trial while the scan is suspended
        advanceUntilIdle() // the scan now finishes with NONE and caches it in the account cache
        scan.await()
        advanceUntilIdle()

        // Trial is layered over the token cache on the display flow: the finished NONE scan cannot mask the
        // activated trial.
        assertEquals(PremiumType.TRIAL, useCase.premiumTypesFlow.value["ta1"])
    }

    // ==================== persistent cache tests ====================

    @Test
    fun hydration_populatesFlowFromDisk() = runTest(dispatcher) {
        stubNoAccounts()
        coEvery { accountPremiumCacheDao.getAll() } returns listOf(
            AccountPremiumCacheEntity("h1", PremiumType.PIRATE, 123L)
        )

        useCase = createUseCase()
        advanceUntilIdle()

        // No accounts to scan, so the hydrated value from disk is what the display flow shows.
        assertEquals(PremiumType.PIRATE, useCase.premiumTypesFlow.value["h1"])
    }

    @Test
    fun hydratedType_shownWhileScanInFlight_thenUpdatedByScan() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "seq1")
        stubActiveAccount(account, level = UserManager.DEFAULT_USER_LEVEL)
        coEvery { accountPremiumCacheDao.getAll() } returns listOf(
            AccountPremiumCacheEntity("seq1", PremiumType.PIRATE, 1L)
        )
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xseq1"
        // Balance lookup suspends, so the warm-up scan is in flight and has not overwritten the hydrated value.
        coEvery { binanceApi.getTokenBalance(any(), "0xseq1") } coAnswers {
            delay(1000)
            TokenBalance(BigDecimal.ZERO)
        }

        useCase = createUseCase()
        runCurrent() // hydration ran, then the warm-up scan started and suspended at the balance lookup

        // Ordering proof: before the scan completes, the display flow already shows the hydrated type.
        assertEquals(PremiumType.PIRATE, useCase.premiumTypesFlow.value["seq1"])

        advanceUntilIdle() // the scan completes and overwrites the token cache with the fresh result
        assertEquals(PremiumType.NONE, useCase.premiumTypesFlow.value["seq1"])
    }

    @Test
    fun hydratedEntry_isForceRescannedByWarmUp() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "a1")
        stubActiveAccount(account, level = UserManager.DEFAULT_USER_LEVEL)
        coEvery { accountPremiumCacheDao.getAll() } returns listOf(
            AccountPremiumCacheEntity("a1", PremiumType.PIRATE, 1L)
        )
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xa1"
        coEvery { binanceApi.getTokenBalance(any(), "0xa1") } returns TokenBalance(BigDecimal.ZERO)

        useCase = createUseCase()
        advanceUntilIdle()

        // Hydrated entry (checkedAtNanos = null) is not fresh, so the warm-up rescans it this launch.
        coVerify(atLeast = 1) { getBnbAddressUseCase.getAddress(account) }
    }

    @Test
    fun activeTrial_hydratedTokenStillForceScanned() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "at1", level = 1)
        stubActiveAccount(account, level = 1)
        coEvery { premiumUserRepository.getByLevel(any()) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null
        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { accountPremiumCacheDao.getAll() } returns listOf(
            AccountPremiumCacheEntity("at1", PremiumType.PIRATE, 1L)
        )
        // Trial is active, but the token-only warm-up must still rescan the token status.
        coEvery {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(account)
        } returns TrialPremiumResult.DemoActive(daysLeft = 5)
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xat1"
        coEvery { binanceApi.getTokenBalance(any(), "0xat1") } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), "0xat1") } throws IllegalStateException("x")

        useCase = createUseCase()
        advanceUntilIdle()

        coVerify(atLeast = 1) { getBnbAddressUseCase.getAddress(account) }
    }

    @Test
    fun startup_noDeletedAccounts_doesNotPrune() = runTest(dispatcher) {
        stubNoAccounts()

        useCase = createUseCase()
        advanceUntilIdle()

        coVerify(exactly = 0) { accountPremiumCacheDao.deleteByAccountIds(any()) }
    }

    @Test
    fun accountDeleted_prunesDeletedIdFromCacheAndDisk() = runTest(dispatcher) {
        stubNoAccounts()
        every { accountManager.getDeletedAccountIds() } returns listOf("d1")
        coEvery { accountPremiumCacheDao.getAll() } returns listOf(
            AccountPremiumCacheEntity("d1", PremiumType.PIRATE, 1L),
            AccountPremiumCacheEntity("k1", PremiumType.COSA, 1L)
        )

        useCase = createUseCase()
        advanceUntilIdle()

        coVerify { accountPremiumCacheDao.deleteByAccountIds(listOf("d1")) }
        assertEquals(null, useCase.premiumTypesFlow.value["d1"])
        assertEquals(PremiumType.COSA, useCase.premiumTypesFlow.value["k1"])
    }

    @Test
    fun accountDeleted_atRuntime_prunesViaAccountsFlowEmission() = runTest(dispatcher) {
        val accountsSharedFlow = MutableSharedFlow<List<Account>>(extraBufferCapacity = 10)
        every { userManager.currentUserLevelFlow } returns MutableStateFlow(UserManager.DEFAULT_USER_LEVEL)
        every { accountManager.accountsFlow } returns accountsSharedFlow
        every { accountManager.accounts } returns emptyList()
        every { accountManager.activeAccount } returns null
        coEvery { accountPremiumCacheDao.getAll() } returns listOf(
            AccountPremiumCacheEntity("d1", PremiumType.PIRATE, 1L)
        )
        every { accountManager.getDeletedAccountIds() } returns emptyList()

        useCase = createUseCase()
        advanceUntilIdle()
        // No deletion at startup: nothing pruned, hydrated entry intact.
        coVerify(exactly = 0) { accountPremiumCacheDao.deleteByAccountIds(any()) }
        assertEquals(PremiumType.PIRATE, useCase.premiumTypesFlow.value["d1"])

        // Account deleted at runtime: the id becomes deleted and accountsFlow emits an invalidation.
        every { accountManager.getDeletedAccountIds() } returns listOf("d1")
        accountsSharedFlow.emit(emptyList())
        advanceUntilIdle()

        coVerify { accountPremiumCacheDao.deleteByAccountIds(listOf("d1")) }
        assertEquals(null, useCase.premiumTypesFlow.value["d1"])
    }

    @Test
    fun scan_persistsDefinitiveTypeToDisk() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "p1")
        stubNoAccounts()
        every { accountManager.account("p1") } returns account
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xp1"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xp1")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)

        useCase = createUseCase()
        advanceUntilIdle()

        useCase.getPremiumTypesForAccounts(listOf(account))
        advanceUntilIdle()

        coVerify {
            accountPremiumCacheDao.upsert(match { it.accountId == "p1" && it.premiumType == PremiumType.PIRATE })
        }
    }

    @Test
    fun scan_indeterminateResult_notPersisted() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "o1")
        stubNoAccounts()
        every { accountManager.account("o1") } returns account
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xo1"
        coEvery { binanceApi.getTokenBalance(any(), "0xo1") } returns null
        coEvery { piratePlaceRepository.getInvestmentData(any(), "0xo1") } throws IllegalStateException("outage")

        useCase = createUseCase()
        advanceUntilIdle()

        useCase.getPremiumTypesForAccounts(listOf(account))
        advanceUntilIdle()

        coVerify(exactly = 0) { accountPremiumCacheDao.upsert(match { it.accountId == "o1" }) }
    }

    @Test
    fun premiumTypesFlow_trialLayeredOverToken() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "l1")
        stubNoAccounts()
        every { accountManager.account("l1") } returns account
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xl1"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xl1")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)
        coEvery {
            activateTrialPremiumUseCase.activateTrialPremium("l1")
        } returns TrialPremiumResult.DemoActive(daysLeft = 1)

        useCase = createUseCase()
        advanceUntilIdle()

        useCase.getPremiumTypesForAccounts(listOf(account))
        advanceUntilIdle()
        assertEquals(PremiumType.PIRATE, useCase.premiumTypesFlow.value["l1"])

        useCase.activateTrialPremium("l1")
        advanceUntilIdle()
        assertEquals(PremiumType.TRIAL, useCase.premiumTypesFlow.value["l1"])
    }

    @Test
    fun concurrentTrialActivation_duringUpdate_notLost() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "keep", level = 1)
        stubActiveAccount(account, level = 1)
        coEvery { premiumUserRepository.getByLevel(any()) } returns null
        coEvery { premiumUserRepository.insert(any()) } returns Unit
        coEvery { premiumUserRepository.deleteByAccount(any()) } returns Unit
        every { checkAdapterPremiumBalanceUseCase.invoke() } returns null
        coEvery { getBnbAddressUseCase.deleteExcludeAccountIds(any()) } returns Unit
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xkeep"
        coEvery { binanceApi.getTokenBalance(any(), "0xkeep") } returns TokenBalance(BigDecimal.ZERO)
        coEvery { piratePlaceRepository.getInvestmentData(any(), "0xkeep") } throws IllegalStateException("x")
        coEvery {
            activateTrialPremiumUseCase.activateTrialPremium("keep")
        } returns TrialPremiumResult.DemoActive(daysLeft = 1)
        // The rebuild holds `mutex` while it slowly reads trial status; the concurrent activation must wait
        // and apply AFTER the full replace instead of being overwritten by it.
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } coAnswers {
            delay(500)
            TrialPremiumResult.NeedPremium
        }

        useCase = createUseCase()
        runCurrent() // init's update() -> updateTrialPremium build holds `mutex`, suspended in the delay
        val activation = launch { useCase.activateTrialPremium("keep") } // blocks on `mutex`
        runCurrent()
        advanceUntilIdle() // rebuild publishes {}, then the activation acquires `mutex` and adds "keep"
        activation.join()

        assertEquals(PremiumType.TRIAL, useCase.premiumTypesFlow.value["keep"])
    }

    @Test
    fun diskWriteFails_scanStillReturnsType() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "w1")
        stubNoAccounts()
        every { accountManager.account("w1") } returns account
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xw1"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xw1")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)
        coEvery { accountPremiumCacheDao.upsert(any()) } throws IllegalStateException("disk full")

        useCase = createUseCase()
        advanceUntilIdle()

        assertEquals(PremiumType.PIRATE, useCase.getPremiumTypesForAccounts(listOf(account))["w1"])
    }

    @Test
    fun diskReadFails_hydrationDoesNotKillInit() = runTest(dispatcher) {
        val account = mnemonicAccount(id = "r1")
        stubActiveAccount(account, level = UserManager.DEFAULT_USER_LEVEL)
        coEvery { accountPremiumCacheDao.getAll() } throws IllegalStateException("db locked")
        coEvery { checkTrialPremiumUseCase.checkTrialPremiumStatus(account) } returns TrialPremiumResult.NeedPremium
        coEvery { getBnbAddressUseCase.getAddress(account) } returns "0xr1"
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, "0xr1")
        } returns TokenBalance(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal() + BigDecimal.ONE)

        useCase = createUseCase()
        advanceUntilIdle()

        // Hydration threw, but the init pipeline continued: the warm-up still scanned and published.
        assertEquals(PremiumType.PIRATE, useCase.premiumTypesFlow.value["r1"])
    }

    private fun stubNoAccounts() {
        every { userManager.currentUserLevelFlow } returns MutableStateFlow(UserManager.DEFAULT_USER_LEVEL)
        every { accountManager.accountsFlow } returns MutableStateFlow(emptyList())
        every { accountManager.accounts } returns emptyList()
        every { accountManager.activeAccount } returns null
    }

    private fun createUseCase(): CheckPremiumUseCaseImpl {
        coEvery { demoPremiumUserDao.hasActiveTrialPremium() } returns false
        return CheckPremiumUseCaseImpl(
            premiumUserRepository = premiumUserRepository,
            demoPremiumUserDao = demoPremiumUserDao,
            binanceApi = binanceApi,
            piratePlaceRepository = piratePlaceRepository,
            accountManager = accountManager,
            checkAdapterPremiumBalanceUseCase = checkAdapterPremiumBalanceUseCase,
            checkTrialPremiumUseCase = checkTrialPremiumUseCase,
            activateTrialPremiumUseCase = activateTrialPremiumUseCase,
            getBnbAddressUseCase = getBnbAddressUseCase,
            userManager = userManager,
            accountPremiumCacheDao = accountPremiumCacheDao,
            dispatcherProvider = testDispatcherProvider
        )
    }

    private fun stubActiveAccount(account: Account, level: Int = 1) {
        val levelFlow = MutableStateFlow(level)
        every { userManager.currentUserLevelFlow } returns levelFlow
        val accountsFlow = MutableStateFlow(listOf(account))
        every { accountManager.accountsFlow } returns accountsFlow
        every { accountManager.accounts } returns listOf(account)
        every { accountManager.activeAccount } returns account
        every { accountManager.account(account.id) } returns account
    }

    private fun stubTwoLevelAccounts(
        mainAccount: Account,
        duressAccount: Account,
        currentLevel: Int
    ) {
        val levelFlow = MutableStateFlow(currentLevel)
        every { userManager.currentUserLevelFlow } returns levelFlow
        val allAccounts = listOf(mainAccount, duressAccount)
        val accountsFlow = MutableStateFlow(allAccounts)
        every { accountManager.accountsFlow } returns accountsFlow
        every { accountManager.accounts } returns allAccounts
        every { accountManager.activeAccount } returns if (currentLevel == 0) mainAccount else duressAccount
        every { accountManager.account(mainAccount.id) } returns mainAccount
        every { accountManager.account(duressAccount.id) } returns duressAccount
    }

    private fun mnemonicAccount(id: String = "account-id", level: Int = 1): Account = Account(
        id = id,
        name = "Account",
        type = AccountType.Mnemonic(
            words = List(12) { "abandon" },
            passphrase = ""
        ),
        origin = AccountOrigin.Created,
        level = level,
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
