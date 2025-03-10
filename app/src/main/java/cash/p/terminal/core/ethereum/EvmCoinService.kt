package cash.p.terminal.core.ethereum

import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.entities.CoinValue
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.modules.send.SendModule
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.CurrencyManager
import java.math.BigDecimal
import java.math.BigInteger

class EvmCoinService(
    val token: Token,
    private val currencyManager: CurrencyManager,
    private val marketKit: MarketKitWrapper
) : Clearable {

    val rate: CurrencyValue?
        get() {
            val baseCurrency = currencyManager.baseCurrency
            return marketKit.coinPrice(token.coin.uid, baseCurrency.code)?.let {
                CurrencyValue(baseCurrency, it.value)
            }
        }

    fun amountData(value: BigInteger, approximate: Boolean = false): SendModule.AmountData {
        val decimalValue = BigDecimal(value, token.decimals)
        val coinValue = CoinValue(token, decimalValue)

        val primaryAmountInfo = SendModule.AmountInfo.CoinValueInfo(coinValue, approximate)
        val secondaryAmountInfo = rate?.let {
            SendModule.AmountInfo.CurrencyValueInfo(CurrencyValue(it.currency, it.value * decimalValue), approximate)
        }

        return SendModule.AmountData(primaryAmountInfo, secondaryAmountInfo)
    }

    fun amountData(value: BigDecimal): SendModule.AmountData {
        return amountData(value.movePointRight(token.decimals).toBigInteger())
    }

    fun coinValue(value: BigInteger): CoinValue {
        return CoinValue(token, convertToMonetaryValue(value))
    }

    fun convertToMonetaryValue(value: BigInteger): BigDecimal {
        return value.toBigDecimal().movePointLeft(token.decimals).stripTrailingZeros()
    }

    fun convertToFractionalMonetaryValue(value: BigDecimal): BigInteger {
        return value.movePointRight(token.decimals).toBigInteger()
    }

    override fun clear() = Unit
}
