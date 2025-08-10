package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.data.model.PremiumUser
import cash.p.terminal.premium.data.repository.PremiumUserRepository
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.premium.domain.model.TokenBalance
import cash.p.terminal.premium.domain.repository.TokenBalanceRepository
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CheckPremiumUseCaseImpl(
    private val premiumUserRepository: PremiumUserRepository,
    private val tokenBalanceRepository: TokenBalanceRepository,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val accountManager: IAccountManager,
    private val seedToEvmAddressUseCase: SeedToEvmAddressUseCase,
    private val checkTrialPremiumUseCase: CheckTrialPremiumUseCase,
    private val activateTrialPremiumUseCase: ActivateTrialPremiumUseCase,
) : CheckPremiumUseCase {

    private val mutex = Mutex()

    private val _premiumCache = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val _trialPremiumCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    override fun startAccountMonitorUpdate() {
        accountManager.accountsFlow
            .onEach {
                _premiumCache.value = emptyMap()
                update()
            }
            .catch { error ->
                println("Error in auto update: $error")
            }
            .launchIn(scope)
    }

    override fun isPremium(): Boolean {
        val currentAccount = accountManager.activeAccount ?: return false
        if (_trialPremiumCache.value[currentAccount.id] == true) {
            return true
        }
        return _premiumCache.value[currentAccount.level] ?: false
    }

    override suspend fun update(): Boolean = mutex.withLock {
        val currentLevel = accountManager.activeAccount?.level ?: return false
        val firstAccountToCheck = premiumUserRepository.getByLevel(currentLevel)

        val cachedResult = checkCachedPremiumStatus(firstAccountToCheck)
        if (cachedResult != null) {
            updateCache(currentLevel, cachedResult)
        }

        if (cachedResult == null || !cachedResult) {
            updateTrialPremium()
        }

        if (cachedResult == null) {
            val premiumStatus = checkPremiumStatusByBalance(firstAccountToCheck, currentLevel)
            val result = premiumStatus ?: firstAccountToCheck?.isPremium ?: false
            updateCache(currentLevel, result)
        }

        return isPremium()
    }

    private fun updateCache(level: Int, isPremium: Boolean) {
        _premiumCache.value = _premiumCache.value + (level to isPremium)
    }

    private suspend fun checkCachedPremiumStatus(firstAccountToCheck: PremiumUser?): Boolean? {
        if (firstAccountToCheck == null) return null

        if (accountManager.account(firstAccountToCheck.accountId) == null) {
            premiumUserRepository.deleteByAccount(firstAccountToCheck.address)
            return null
        }

        val isWithinCheckInterval = System.currentTimeMillis() - firstAccountToCheck.lastCheckDate <
                PremiumConfig.PREMIUM_CHECK_INTERVAL

        return if (isWithinCheckInterval) firstAccountToCheck.isPremium else null
    }

    private suspend fun checkPremiumStatusByBalance(
        firstAccountToCheck: PremiumUser?,
        currentLevel: Int
    ): Boolean? {
        val accountsToCheck = getAccountsToCheck(firstAccountToCheck?.accountId)
        var lastCheckedAddress: String? = null
        var lastCheckedAccount: Account? = null

        var balanceReceived = false // false means no internet connection or no balance received
        for (account in accountsToCheck) {
            if (account.isWatchAccount || account.type !is AccountType.Mnemonic || !account.hasAnyBackup) continue

            val mnemonicType = account.type as AccountType.Mnemonic
            val address = seedToEvmAddressUseCase(mnemonicType.words, mnemonicType.passphrase)
            lastCheckedAddress = address
            lastCheckedAccount = account

            for (coinType in coinConfigs.keys) {
                val result = isPremiumByBalance(coinType, address)
                balanceReceived = balanceReceived || result != null
                if (result == true) {
                    updatePremiumData(address, currentLevel, coinType, account, isPremium = true)
                    return true
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
                isPremium = false
            )
        }

        return false
    }

    private suspend fun updatePremiumData(
        address: String,
        currentLevel: Int,
        coinType: String,
        account: Account,
        isPremium: Boolean
    ) {
        premiumUserRepository.insert(
            PremiumUser(
                address = address,
                level = currentLevel,
                coinType = coinType,
                isPremium = isPremium,
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
        return tokenBalanceRepository.getTokenBalance(
            rpcUrl = PremiumConfig.BSC_RPC_URL,
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
        _trialPremiumCache.value = _trialPremiumCache.value + (address to true)
    }
}

internal data class CoinConfig(
    val contractAddress: String,
    val minAmount: Int
)