package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.network.pirate.domain.useCase.FiatCurrencyRateService
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.Currency
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.rx2.asFlow

class AssetFiatRateService(
    private val marketKit: MarketKitWrapper,
    private val fiatCurrencyRateService: FiatCurrencyRateService,
) {
    suspend fun rate(token: Token, currency: Currency): BigDecimal? {
        return if (PayCoreAssets.isFiat(token)) {
            fiatCurrencyRateService.rate(token.coin.code, currency.code)
        } else {
            marketKit.coinPrice(token.coin.uid, currency.code)?.value
        }
    }

    fun rateFlow(tag: String, token: Token, currency: Currency): Flow<BigDecimal?> {
        return if (PayCoreAssets.isFiat(token)) {
            flow { emit(rate(token, currency)) }
        } else {
            marketKit.coinPriceObservable(tag, token.coin.uid, currency.code)
                .asFlow()
                .map { it.value }
                .onStart {
                    rate(token, currency)?.let { emit(it) }
                }
        }
    }
}
