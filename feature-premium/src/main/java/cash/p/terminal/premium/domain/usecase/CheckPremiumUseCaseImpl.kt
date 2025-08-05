package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.data.model.PremiumUser
import cash.p.terminal.premium.data.repository.PremiumUserRepository
import cash.p.terminal.premium.domain.model.TokenBalance
import cash.p.terminal.premium.domain.repository.TokenBalanceRepository
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CheckPremiumUseCaseImpl(
    private val premiumUserRepository: PremiumUserRepository,
    private val tokenBalanceRepository: TokenBalanceRepository,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val accountManager: IAccountManager,
    private val seedToEvmAddressUseCase: SeedToEvmAddressUseCase
) : CheckPremiumUseCase {

    private val mutex = Mutex()

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

    override suspend operator fun invoke(): Boolean = mutex.withLock {
        val currentLevel = accountManager.activeAccount?.level ?: return false
        val firstAccountToCheck = premiumUserRepository.getByLevel(currentLevel)

        val cachedResult = checkCachedPremiumStatus(firstAccountToCheck)
        if (cachedResult != null) return cachedResult

        val premiumStatus = checkPremiumStatusByBalance(firstAccountToCheck, currentLevel)
        return premiumStatus ?: firstAccountToCheck?.isPremium ?: false
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

        for (account in accountsToCheck) {
            if (account.isWatchAccount || account.type !is AccountType.Mnemonic || !account.hasAnyBackup) continue

            val mnemonicType = account.type as AccountType.Mnemonic
            val address = seedToEvmAddressUseCase(mnemonicType.words, mnemonicType.passphrase)
            lastCheckedAddress = address
            lastCheckedAccount = account

            for (coinType in coinConfigs.keys) {
                if (isPremiumByBalance(coinType, address) == true) {
                    updatePremiumData(address, currentLevel, coinType, account, isPremium = true)
                    return true
                }
            }
        }

        if (lastCheckedAddress != null && lastCheckedAccount != null) {
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
}

internal data class CoinConfig(
    val contractAddress: String,
    val minAmount: Int
)