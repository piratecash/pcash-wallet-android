package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.ServiceState
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.Currency
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class FiatService(
    private val assetFiatRateService: AssetFiatRateService,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ServiceState<FiatService.State>(), AutoCloseable {
    private var currency: Currency? = null
    private var token: Token? = null
    private var amount: BigDecimal? = null
    private var rate: BigDecimal? = null

    private var fiatAmount: BigDecimal? = null
    private var rateUpdatesJob: Job? = null

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(dispatcher + job)

    override fun createState() = State(
        rate = rate,
        amount = amount,
        fiatAmount = fiatAmount
    )

    private fun refreshRate() {
        rate = null
        resubscribeForRate()
    }

    private fun refreshFiatAmount() {
        fiatAmount = amount?.let { amount ->
            rate?.let { rate ->
                currency?.let { currency ->
                    (amount * rate).setScale(currency.decimal, RoundingMode.DOWN).stripTrailingZeros()
                }
            }
        }
    }

    private fun refreshAmount() {
        amount = fiatAmount?.let { fiatAmount ->
            rate?.takeIf { it > BigDecimal.ZERO }?.let { rate ->
                token?.let { token ->
                    fiatAmount.divide(rate, token.decimals, RoundingMode.DOWN).stripTrailingZeros()
                }
            }
        }
    }

    private fun resubscribeForRate() {
        rateUpdatesJob?.cancel()
        val currency = currency ?: return

        token?.let { token ->
            rateUpdatesJob = coroutineScope.launch {
                assetFiatRateService.rateFlow("swap", token, currency)
                    .collect {
                        rate = it
                        refreshFiatAmount()
                        emitState()
                    }
            }
        }
    }

    fun setCurrency(currency: Currency) {
        if (this.currency == currency) return

        this.currency = currency

        refreshRate()
        refreshFiatAmount()

        emitState()
    }

    fun setToken(token: Token?) {
        if (this.token == token) return

        this.token = token

        refreshRate()
        refreshFiatAmount()

        emitState()
    }

    fun setAmount(amount: BigDecimal?) {
        if (this.amount == amount) return

        this.amount = amount
        refreshFiatAmount()

        emitState()
    }

    fun setFiatAmount(fiatAmount: BigDecimal?) {
        if (this.fiatAmount == fiatAmount) return

        this.fiatAmount = fiatAmount
        refreshAmount()

        emitState()
    }

    override fun close() {
        coroutineScope.cancel()
    }

    data class State(
        val amount: BigDecimal?,
        val fiatAmount: BigDecimal?,
        val rate: BigDecimal?
    )
}
