package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.feature.miniapp.data.api.MiniAppApi
import cash.p.terminal.feature.miniapp.data.api.toDomain
import cash.p.terminal.feature.miniapp.domain.model.CoinType
import cash.p.terminal.feature.miniapp.domain.model.SpecialProposalData
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.pirate.domain.enity.PeriodType
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.domain.usecase.GetBnbAddressUseCase
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

class GetSpecialProposalDataUseCase(
    private val currencyManager: CurrencyManager,
    private val numberFormatter: IAppNumberFormatter,
    private val miniAppApi: MiniAppApi,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val marketKitWrapper: MarketKitWrapper,
    private val binanceApi: BinanceApi,
    private val getBnbAddressUseCase: GetBnbAddressUseCase,
    private val accountManager: IAccountManager,
    private val dispatcherProvider: DispatcherProvider,
    private val codeStorage: IUniqueCodeStorage
) {
    suspend operator fun invoke(
        selectedAccountId: String,
        jwt: String,
        endpoint: String,
        currencyCode: String
    ): SpecialProposalData = withContext(dispatcherProvider.io) {
        // Fetch API profile for guaranteed bonus
        val profileDeferred = async { miniAppApi.getUserProfile(jwt, endpoint) }

        // Get EVM address for balance fetching
        val evmAddress = accountManager.account(selectedAccountId)?.let { account ->
            getBnbAddressUseCase.getAddress(account)
        }

        // Get remote balances
        val pirateBalance = evmAddress?.let {
            getRemoteBalance(
                PremiumConfig.PIRATE_CONTRACT_ADDRESS,
                it,
                PremiumConfig.COIN_TYPE_PIRATE
            )
        } ?: BigDecimal.ZERO
        val cosaBalance = evmAddress?.let {
            getRemoteBalance(
                PremiumConfig.COSANTA_CONTRACT_ADDRESS,
                it,
                PremiumConfig.COIN_TYPE_COSANTA
            )
        } ?: BigDecimal.ZERO

        // Get prices
        val piratePrice =
            marketKitWrapper.coinPrice(BlockchainType.PirateCash.uid, currencyCode)?.value
                ?: BigDecimal.ZERO
        val cosaPrice = marketKitWrapper.coinPrice(BlockchainType.Cosanta.uid, currencyCode)?.value
            ?: BigDecimal.ZERO

        // Calculate income projections
        val pirateCalcDeferred = async {
            runCatching {
                val amount =
                    if (pirateBalance >= BigDecimal(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE)) {
                        pirateBalance.toDouble()
                    } else {
                        PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toDouble()
                    }
                piratePlaceRepository.getCalculatorData("pirate", amount)
            }.getOrNull()
        }

        val cosaCalcDeferred = async {
            runCatching {
                val amount =
                    if (cosaBalance >= BigDecimal(PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA)) {
                        cosaBalance.toDouble()
                    } else {
                        PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA.toDouble()
                    }
                piratePlaceRepository.getCalculatorData("cosa", amount)
            }.getOrNull()
        }

        val profile = profileDeferred.await().toDomain()
        val pirateCalcData = pirateCalcDeferred.await()
        val cosaCalcData = cosaCalcDeferred.await()

        // Cache balance for MiniApp screen (same format as GetMiniAppBalanceUseCase)
        profile.balance.movePointLeft(8).let {
            codeStorage.cachedBalance = it.toPlainString()
        }

        // Calculate guaranteed bonus
        // 8 decimal places and take 10%
        val guaranteedBonus = profile.balance.movePointLeft(9).max(BigDecimal.ONE).toInt()
        val guaranteedBonusFiat = formatFiat(BigDecimal(guaranteedBonus) * piratePrice)

        // Calculate "not enough" amounts
        val pirateNotEnoughAmount = maxOf(
            BigDecimal.ZERO,
            BigDecimal(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE) - pirateBalance
        )
        val cosaNotEnoughAmount = maxOf(
            BigDecimal.ZERO,
            BigDecimal(PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA) - cosaBalance
        )
        val pirateNotEnough = numberFormatter.formatCoinFull(pirateNotEnoughAmount, "PIRATE", 8)
        val cosaNotEnough = numberFormatter.formatCoinFull(cosaNotEnoughAmount, "COSA", 8)
        val pirateNotEnoughFiat = formatFiat(pirateNotEnoughAmount * piratePrice)
        val cosaNotEnoughFiat = formatFiat(cosaNotEnoughAmount * cosaPrice)

        // Get monthly income
        val pirateMonthlyData = pirateCalcData?.items?.find { it.periodType == PeriodType.MONTH }
        val cosaMonthlyData = cosaCalcData?.items?.find { it.periodType == PeriodType.MONTH }

        val pirateMonthlyIncome = formatCoinAmount(pirateMonthlyData?.amount ?: 0.0, "PIRATE")
        val pirateMonthlyIncomeFiat = formatFiat(
            BigDecimal(pirateMonthlyData?.amount ?: 0.0) * piratePrice
        )
        val cosaMonthlyIncome = formatCoinAmount(cosaMonthlyData?.amount ?: 0.0, "COSA")
        val cosaMonthlyIncomFiat = formatFiat(
            BigDecimal(cosaMonthlyData?.amount ?: 0.0) * cosaPrice
        )

        // Get ROI (from yearly data)
        val pirateYearlyData = pirateCalcData?.items?.find { it.periodType == PeriodType.YEAR }
        val cosaYearlyData = cosaCalcData?.items?.find { it.periodType == PeriodType.YEAR }
        val pirateRoi = calculateRoi(
            pirateYearlyData?.amount,
            PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE.toDouble()
        )
        val cosaRoi = calculateRoi(
            cosaYearlyData?.amount,
            PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA.toDouble()
        )

        // Determine cheaper option
        val pirateNeededUsd = pirateNotEnoughAmount * piratePrice
        val cosaNeededUsd = cosaNotEnoughAmount * cosaPrice
        val cheaperOption = if (pirateNeededUsd <= cosaNeededUsd) CoinType.PIRATE else CoinType.COSA

        // Premium status
        val hasPiratePremium = pirateBalance >= BigDecimal(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE)
        val hasCosaPremium = cosaBalance >= BigDecimal(PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA)

        SpecialProposalData(
            guaranteedBonus = guaranteedBonus,
            guaranteedBonusFiat = guaranteedBonusFiat,
            pirateBalance = pirateBalance,
            pirateNotEnough = pirateNotEnough,
            pirateNotEnoughFiat = pirateNotEnoughFiat,
            pirateRoi = pirateRoi,
            pirateMonthlyIncome = pirateMonthlyIncome,
            pirateMonthlyIncomeFiat = pirateMonthlyIncomeFiat,
            cosaBalance = cosaBalance,
            cosaNotEnough = cosaNotEnough,
            cosaNotEnoughFiat = cosaNotEnoughFiat,
            cosaRoi = cosaRoi,
            cosaMonthlyIncome = cosaMonthlyIncome,
            cosaMonthlyIncomeFiat = cosaMonthlyIncomFiat,
            cheaperOption = cheaperOption,
            hasPiratePremium = hasPiratePremium,
            hasCosaPremium = hasCosaPremium,
            hasPremium = hasPiratePremium || hasCosaPremium
        )
    }

    private suspend fun getRemoteBalance(
        contractAddress: String,
        address: String,
        coinType: String
    ): BigDecimal {
        return binanceApi.getTokenBalance(
            contractAddress = contractAddress,
            walletAddress = address
        )?.balance ?: runCatching {
            piratePlaceRepository.getInvestmentData(coinType, address)
                .balance.toBigDecimal()
        }.getOrNull() ?: BigDecimal.ZERO
    }

    private fun formatFiat(amount: BigDecimal): String {
        val currency = currencyManager.baseCurrency
        return numberFormatter.formatFiatShort(amount, currency.symbol, currency.decimal)
    }

    private fun formatCoinAmount(amount: Double, symbol: String): String {
        val formatted = BigDecimal(amount).setScale(4, RoundingMode.HALF_UP)
        return "+$formatted $symbol"
    }

    private fun calculateRoi(yearlyIncome: Double?, principal: Double): String {
        if (yearlyIncome == null || principal <= 0) return "-"
        val roi = (yearlyIncome / principal) * 100
        return "${BigDecimal(roi).setScale(1, RoundingMode.FLOOR)}%"
    }
}
