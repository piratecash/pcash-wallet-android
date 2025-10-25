package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.eligibleForPremium
import cash.p.terminal.wallet.isCosanta
import cash.p.terminal.wallet.isPirateCash
import java.math.BigDecimal

internal interface CheckAdapterPremiumBalanceUseCase {
    operator fun invoke(): Result?

    sealed class Result(
        open val account: Account,
        open val wallet: Wallet,
        open val address: String,
        open val coinType: String
    ) {
        data class Premium(
            override val account: Account,
            override val wallet: Wallet,
            override val address: String,
            override val coinType: String,
            val premiumType: PremiumType
        ) : Result(account, wallet, address, coinType)

        data class Insufficient(
            override val account: Account,
            override val wallet: Wallet,
            override val address: String,
            override val coinType: String
        ) : Result(account, wallet, address, coinType)
    }
}

internal class CheckAdapterPremiumBalanceUseCaseImpl(
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager
) : CheckAdapterPremiumBalanceUseCase {

    private data class Requirement(
        val matcher: (Wallet) -> Boolean,
        val threshold: BigDecimal,
        val premiumType: PremiumType,
        val coinType: String
    )

    private val requirements = listOf(
        Requirement(
            matcher = { it.isPirateCash() },
            threshold = PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toBigDecimal(),
            premiumType = PremiumType.PIRATE,
            coinType = PremiumConfig.COIN_TYPE_PIRATE
        ),
        Requirement(
            matcher = { it.isCosanta() },
            threshold = PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA.toBigDecimal(),
            premiumType = PremiumType.COSA,
            coinType = PremiumConfig.COIN_TYPE_COSANTA
        )
    )

    override fun invoke(): CheckAdapterPremiumBalanceUseCase.Result? {
        val activeAccount = accountManager.activeAccount ?: return null
        if (!activeAccount.eligibleForPremium()) return null

        val accountWallets =
            walletManager.activeWallets.filter { it.account.id == activeAccount.id }
        if (accountWallets.isEmpty()) return null

        var lastChecked: CheckAdapterPremiumBalanceUseCase.Result.Insufficient? = null

        accountWallets.forEach { wallet ->
            val requirement = requirements.firstOrNull { it.matcher(wallet) } ?: return@forEach

            val balanceAdapter =
                adapterManager.getAdapterForWallet<IBalanceAdapter>(wallet) ?: return@forEach

            val receiveAddress =
                adapterManager.getReceiveAdapterForWallet(wallet)?.receiveAddress ?: return@forEach
            val available = balanceAdapter.balanceData.available

            if (available >= requirement.threshold) {
                return CheckAdapterPremiumBalanceUseCase.Result.Premium(
                    account = activeAccount,
                    wallet = wallet,
                    address = receiveAddress,
                    coinType = requirement.coinType,
                    premiumType = requirement.premiumType
                )
            } else {
                lastChecked = CheckAdapterPremiumBalanceUseCase.Result.Insufficient(
                    account = activeAccount,
                    wallet = wallet,
                    address = receiveAddress,
                    coinType = requirement.coinType
                )
            }
        }

        return lastChecked
    }
}
