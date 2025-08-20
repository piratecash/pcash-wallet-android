package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.model.DemoPremiumUser
import cash.p.terminal.wallet.IAccountManager
import timber.log.Timber

internal class ActivateTrialPremiumUseCase(
    private val demoPremiumUserDao: DemoPremiumUserDao,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val accountManager: IAccountManager,
    private val getBnbAddressUseCase: GetBnbAddressUseCase
) {

    suspend fun activateTrialPremium(accountId: String): TrialPremiumResult {
        val account = accountManager.account(accountId) ?: return TrialPremiumResult.DemoError()

        var walletAddressLog: String? = null
        return try {
            val walletAddress = getBnbAddressUseCase.getAddress(account, true)
                ?: throw IllegalStateException("Wallet address not found")
            walletAddressLog = walletAddress
            val premiumStatus = piratePlaceRepository.activateTrialPremium(walletAddress)

            when (premiumStatus) {
                is TrialPremiumResult.DemoNotFound,
                is TrialPremiumResult.DemoExpired -> {
                    if(demoPremiumUserDao.getByAddress(walletAddress) != null) {
                        // Update cache with expired status
                        demoPremiumUserDao.insert(
                            DemoPremiumUser(
                                address = walletAddress,
                                daysLeft = 0,
                                lastCheckDate = System.currentTimeMillis()
                            )
                        )
                    }
                }

                is TrialPremiumResult.DemoActive -> {
                    // Update cache with new active status
                    demoPremiumUserDao.insert(
                        DemoPremiumUser(
                            address = walletAddress,
                            daysLeft = premiumStatus.daysLeft,
                            lastCheckDate = System.currentTimeMillis()
                        )
                    )
                }

                else -> Unit
            }
            premiumStatus ?: TrialPremiumResult.DemoError()
        } catch (e: Exception) {
            Timber.e(e, "Error activating premium for wallet $walletAddressLog")
            TrialPremiumResult.DemoError()
        }
    }
}
