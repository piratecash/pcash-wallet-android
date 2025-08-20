package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.model.DemoPremiumUser
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.eligibleForPremium
import timber.log.Timber

internal class CheckTrialPremiumUseCase(
    private val piratePlaceRepository: PiratePlaceRepository,
    private val demoPremiumUserDao: DemoPremiumUserDao,
    private val getBnbAddressUseCase: GetBnbAddressUseCaseImpl
) {

    suspend fun checkTrialPremiumStatus(account: Account): TrialPremiumResult {
        if (!account.eligibleForPremium()) {
            return TrialPremiumResult.DemoNotFound
        }

        var cachedUser: DemoPremiumUser? = null
        return try {
            val walletAddress = getBnbAddressUseCase.getAddress(account)
                ?: throw IllegalStateException("Wallet address not found")

            cachedUser = findCachedUser(walletAddress) ?: return TrialPremiumResult.NeedPremium

            val currentTime = System.currentTimeMillis()
            val daysPassed = (currentTime - cachedUser.lastCheckDate) / (24 * 60 * 60 * 1000)
            val remainingDays = cachedUser.daysLeft - daysPassed.toInt()

            when {
                // If days haven't expired yet, return cached result
                remainingDays > 0 -> {
                    return TrialPremiumResult.DemoActive(daysLeft = remainingDays)
                }
                // If cached value is already 0 (expired), don't make network request
                cachedUser.daysLeft == 0 -> {
                    return TrialPremiumResult.DemoExpired
                }
                // If days expired but cached value wasn't 0, check network to get updated value
            }

            // Check network for updated status
            val premiumStatus = piratePlaceRepository.checkTrialPremiumStatus(walletAddress)
            when (premiumStatus) {
                is TrialPremiumResult.DemoNotFound,
                is TrialPremiumResult.DemoExpired -> {
                    // Update cache with expired status
                    demoPremiumUserDao.deleteByAddress(walletAddress)
                }

                is TrialPremiumResult.DemoActive -> {
                    // Update cache with new active status
                    demoPremiumUserDao.insert(
                        cachedUser.copy(
                            daysLeft = premiumStatus.daysLeft,
                            lastCheckDate = System.currentTimeMillis()
                        )
                    )
                }

                else -> Unit
            }
            premiumStatus ?: TrialPremiumResult.DemoActive(daysLeft = cachedUser.daysLeft)
        } catch (e: Exception) {
            Timber.e(e, "Error checking premium status for wallet ${cachedUser?.address}")
            TrialPremiumResult.DemoError()
        }
    }

    private suspend fun findCachedUser(walletAddress: String): DemoPremiumUser? {
        return demoPremiumUserDao.getByAddress(walletAddress)
    }
}