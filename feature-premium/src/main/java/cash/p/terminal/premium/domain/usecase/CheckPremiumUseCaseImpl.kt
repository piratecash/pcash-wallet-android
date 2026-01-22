package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.binance.data.TokenBalance
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.model.PremiumUser
import cash.p.terminal.premium.data.repository.PremiumUserRepository
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.eligibleForPremium
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CheckPremiumUseCaseImpl(
    private val premiumUserRepository: PremiumUserRepository,
    private val demoPremiumUserDao: DemoPremiumUserDao,
    private val binanceApi: BinanceApi,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val accountManager: IAccountManager,
    private val checkAdapterPremiumBalanceUseCase: CheckAdapterPremiumBalanceUseCase,
    private val checkTrialPremiumUseCase: CheckTrialPremiumUseCase,
    private val activateTrialPremiumUseCase: ActivateTrialPremiumUseCase,
    private val getBnbAddressUseCase: GetBnbAddressUseCase,
    private val userManager: UserManager,
    private val dispatcherProvider: DispatcherProvider
) : CheckPremiumUseCase {

    private val mutex = Mutex()

    private val _premiumCache = MutableStateFlow<Map<Int, PremiumType>>(emptyMap())
    private val _trialPremiumCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _levelAccountCache = MutableStateFlow<Map<Int, String>>(emptyMap())
    private val scope = CoroutineScope(dispatcherProvider.default + SupervisorJob())

    private val coinConfigs = mapOf(
        PremiumConfig.COIN_TYPE_PIRATE to CoinConfig(
            contractAddress = PremiumConfig.PIRATE_CONTRACT_ADDRESS,
            minAmount = PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE
        ),
        PremiumConfig.COIN_TYPE_COSANTA to CoinConfig(
            contractAddress = PremiumConfig.COSANTA_CONTRACT_ADDRESS,
            minAmount = PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA
        )
    )

    init {
        scope.launch {
            update()
        }
        scope.launch {
            combine(
                userManager.currentUserLevelFlow,
                accountManager.accountsFlow
            ) { _, _ -> Unit }
                .collectLatest {
                    update()
                }
        }
    }

    override fun getPremiumType(): PremiumType {
        return getPremiumTypeForLevel(userManager.currentUserLevelFlow.value)
    }

    override fun getParentPremiumType(userLevel: Int): PremiumType {
        return getPremiumTypeForLevel(getParentLevel(userLevel))
    }

    override suspend fun isPremiumWithParentInCache(userLevel: Int): Boolean {
            // Check token premium (PIRATE/COSA) by level
            val hasTokenPremium = premiumUserRepository.getByLevels(
                listOf(
                    userLevel,
                    getParentLevel(userLevel)
                )
            ).any { it.isPremium.isPremium() }

            if (hasTokenPremium) return true

            // Check trial premium (any active trial in cache)
            return withContext(dispatcherProvider.io) { demoPremiumUserDao.hasActiveTrialPremium() }
        }

    private fun getParentLevel(userLevel: Int): Int {
        return if (userLevel > 0) userLevel - 1 else userLevel
    }

    private fun getPremiumTypeForLevel(level: Int): PremiumType {
        val currentLevel = userManager.currentUserLevelFlow.value

        // For current level, use active account; for other levels, use cached accountId
        val accountId = if (level == currentLevel) {
            accountManager.activeAccount?.id
        } else {
            _levelAccountCache.value[level]
        }

        // Check trial premium if we have an accountId
        if (accountId != null && _trialPremiumCache.value[accountId] == true) {
            return PremiumType.TRIAL
        }

        val premiumType = _premiumCache.value[level]
        if (premiumType?.isPremium() != true && accountId != null) {
            val adapterResult = checkAdapterPremiumBalanceUseCase()
            if (adapterResult is CheckAdapterPremiumBalanceUseCase.Result.Premium) {
                scope.launch {
                    // to keep cache updated
                    updateAdapterBalance()
                }
                return adapterResult.premiumType
            }
        }
        return premiumType ?: PremiumType.NONE
    }

    override fun isTrialPremium(): Boolean {
        val currentAccount = accountManager.activeAccount ?: return false
        return _trialPremiumCache.value[currentAccount.id] == true
    }

    override suspend fun update(): PremiumType = mutex.withLock {
        _premiumCache.value = emptyMap()
        _levelAccountCache.value = emptyMap()

        val currentLevel = userManager.currentUserLevelFlow.value
        if (currentLevel == UserManager.DEFAULT_USER_LEVEL) {
            return PremiumType.NONE
        }
        val parentLevel = getParentLevel(currentLevel)

        // Update current level
        updateForLevel(currentLevel)

        // Update parent level if different
        if (parentLevel != currentLevel) {
            updateForLevel(parentLevel)
        }

        return getPremiumType()
    }

    private suspend fun updateForLevel(level: Int) {
        var accountToCheck = premiumUserRepository.getByLevel(level)

        // Cache level -> accountId mapping for trial premium lookup
        accountToCheck?.let {
            _levelAccountCache.value += (level to it.accountId)
        }

        val cachedResult = try {
            checkCachedPremiumStatus(accountToCheck)
        } catch (_: Exception) {
            accountToCheck = null
            null
        }
        if (cachedResult != null) {
            updateCache(level, cachedResult)
        }

        if (cachedResult == null || cachedResult == PremiumType.NONE) {
            updateTrialPremium()
        }

        if (cachedResult == null) {
            val newState = updateAdapterBalanceForLevel(level)
            if (newState?.isPremium() == true) {
                return
            }
        }

        if (cachedResult == null) {
            val premiumStatus = checkPremiumStatusByBalance(accountToCheck, level)
            val result = premiumStatus ?: accountToCheck?.isPremium ?: PremiumType.NONE
            updateCache(level, result)
        }
    }

    private suspend fun updateAdapterBalance(): PremiumType? {
        return updateAdapterBalanceForLevel(userManager.currentUserLevelFlow.value)
    }

    private suspend fun updateAdapterBalanceForLevel(level: Int): PremiumType? {
        when (val adapterResult = checkAdapterPremiumBalanceUseCase()) {
            is CheckAdapterPremiumBalanceUseCase.Result.Premium -> {
                updatePremiumData(
                    address = adapterResult.address,
                    currentLevel = level,
                    coinType = adapterResult.coinType,
                    account = adapterResult.account,
                    premiumType = adapterResult.premiumType
                )
                updateCache(level, adapterResult.premiumType)
                return adapterResult.premiumType
            }

            is CheckAdapterPremiumBalanceUseCase.Result.Insufficient -> {
                updatePremiumData(
                    address = adapterResult.address,
                    currentLevel = level,
                    coinType = adapterResult.coinType,
                    account = adapterResult.account,
                    premiumType = PremiumType.NONE
                )
                updateCache(level, PremiumType.NONE)
            }

            null -> Unit
        }
        return null
    }

    private fun updateCache(level: Int, premiumType: PremiumType) {
        _premiumCache.value += (level to premiumType)
    }

    private suspend fun checkCachedPremiumStatus(accountToCheck: PremiumUser?): PremiumType? {
        if (accountToCheck == null) return null

        if (accountManager.account(accountToCheck.accountId) == null) {
            premiumUserRepository.deleteByAccount(accountToCheck.accountId)
            getBnbAddressUseCase.deleteBnbAddress(accountToCheck.accountId)
            error("Account not found")
        }

        val isWithinCheckInterval = System.currentTimeMillis() - accountToCheck.lastCheckDate <
                PremiumConfig.PREMIUM_CHECK_INTERVAL

        return if (isWithinCheckInterval) accountToCheck.isPremium else null
    }

    private suspend fun checkPremiumStatusByBalance(
        firstAccountToCheck: PremiumUser?,
        currentLevel: Int
    ): PremiumType? {
        val accountsToCheck = getAccountsToCheck(firstAccountToCheck?.accountId)
        deleteRemovedAccounts(accountsToCheck)

        var lastCheckedAddress: String? = null
        var lastCheckedAccount: Account? = null

        var balanceReceived = false // false means no internet connection or no balance received
        for (account in accountsToCheck) {
            if (!account.eligibleForPremium()) continue

            val address = getBnbAddressUseCase.getAddress(account) ?: continue
            lastCheckedAddress = address
            lastCheckedAccount = account

            for (coinType in coinConfigs.keys) {
                val result = isPremiumByBalance(coinType, address)
                balanceReceived = balanceReceived || result != null
                if (result == true) {
                    val premiumType = if (coinType == PremiumConfig.COIN_TYPE_PIRATE) {
                        PremiumType.PIRATE
                    } else {
                        PremiumType.COSA
                    }
                    updatePremiumData(
                        address,
                        currentLevel,
                        coinType,
                        account,
                        premiumType = premiumType
                    )
                    return premiumType
                }
            }
        }

        if (!balanceReceived) {
            // No need to update cache if no balance was received
            return null
        }

        if (lastCheckedAddress != null && lastCheckedAccount != null) {
            // means no premium found for any account
            val coinType = coinConfigs.keys.first()
            updatePremiumData(
                address = lastCheckedAddress,
                currentLevel = currentLevel,
                coinType = coinType,
                account = lastCheckedAccount,
                premiumType = PremiumType.NONE
            )
        }

        return PremiumType.NONE
    }

    private suspend fun deleteRemovedAccounts(accounts: List<Account>) {
        val ids = accounts.map { it.id }
        if (ids.isNotEmpty()) {
            getBnbAddressUseCase.deleteExcludeAccountIds(ids)
        }
    }

    private suspend fun updatePremiumData(
        address: String,
        currentLevel: Int,
        coinType: String,
        account: Account,
        premiumType: PremiumType
    ) {
        premiumUserRepository.insert(
            PremiumUser(
                address = address,
                level = currentLevel,
                coinType = coinType,
                isPremium = premiumType,
                accountId = account.id,
                lastCheckDate = System.currentTimeMillis()
            )
        )
    }

    private fun getAccountsToCheck(primaryAccountId: String?) =
        if (primaryAccountId == null) {
            accountManager.accounts
        } else {
            accountManager.accounts.sortedBy { if (it.id == primaryAccountId) 0 else 1 }
        }

    /**
     * Checks if the user is premium based on their token balance.
     * @param coinType The type of the coin (e.g., "PIRATE", "COSANTA").
     * @param address The wallet address to check.
     * @return Boolean? Returns true if the user is premium, false if not, or null if the balance could not be retrieved.
     */
    private suspend fun isPremiumByBalance(coinType: String, address: String): Boolean? {
        val config = coinConfigs[coinType] ?: return false

        val balance = getTokenBalance(config.contractAddress, address, coinType) ?: return null
        return balance.balance >= config.minAmount.toBigDecimal()
    }

    private suspend fun getTokenBalance(
        contractAddress: String,
        address: String,
        coinType: String
    ): TokenBalance? {
        return binanceApi.getTokenBalance(
            contractAddress = contractAddress,
            walletAddress = address
        ) ?: runCatching {
            TokenBalance(
                balance = piratePlaceRepository.getInvestmentData(coinType, address)
                    .balance.toBigDecimal()
            )
        }.getOrNull()
    }

    // Trial Premium

    override suspend fun checkTrialPremiumStatus() = withContext(Dispatchers.IO) {
        accountManager.activeAccount?.let {
            checkTrialPremiumUseCase.checkTrialPremiumStatus(it)
        } ?: run {
            TrialPremiumResult.DemoNotFound
        }
    }

    override suspend fun activateTrialPremium(accountId: String): TrialPremiumResult =
        activateTrialPremiumUseCase.activateTrialPremium(accountId).also {
            if (it is TrialPremiumResult.DemoActive) {
                updateTrialPremiumCache(accountId)
            }
        }

    private suspend fun updateTrialPremium() {
        _trialPremiumCache.value = emptyMap()

        accountManager.accounts.forEach { account ->
            if (checkTrialPremiumUseCase.checkTrialPremiumStatus(account) is TrialPremiumResult.DemoActive) {
                updateTrialPremiumCache(account.id)
            }
        }
    }

    private fun updateTrialPremiumCache(address: String) {
        _trialPremiumCache.value += (address to true)
    }

    override suspend fun checkPremiumByBalanceForAccount(account: Account, checkTrial: Boolean): PremiumType {
        // Check trial premium first
        if (checkTrial && _trialPremiumCache.value[account.id] == true) {
            return PremiumType.TRIAL
        }

        if (!account.eligibleForPremium()) return PremiumType.NONE

        val address = getBnbAddressUseCase.getAddress(account) ?: return PremiumType.NONE

        for (coinType in coinConfigs.keys) {
            val result = isPremiumByBalance(coinType, address)
            if (result == true) {
                return if (coinType == PremiumConfig.COIN_TYPE_PIRATE) {
                    PremiumType.PIRATE
                } else {
                    PremiumType.COSA
                }
            }
        }
        return PremiumType.NONE
    }
}

internal data class CoinConfig(
    val contractAddress: String,
    val minAmount: Int
)
