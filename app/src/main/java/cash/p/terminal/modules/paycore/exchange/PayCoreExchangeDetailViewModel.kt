package cash.p.terminal.modules.paycore.exchange

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.paycore.PayCoreAssetResolver
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.changenow.domain.entity.toStatus
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.coinImageUrl
import io.horizontalsystems.core.IAppNumberFormatter
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PayCoreExchangeDetailViewModel(
    private val date: Long,
    private val storage: SwapProviderTransactionsStorage,
    private val marketKit: MarketKitWrapper,
    private val numberFormatter: IAppNumberFormatter,
) : ViewModel() {

    var uiState by mutableStateOf<PayCoreExchangeDetailUiState?>(null)
        private set

    init {
        viewModelScope.launch {
            storage.observeByDate(date).collect { tx ->
                if (tx == null) return@collect
                val coinCodeIn = PayCoreAssetResolver.coinCode(tx.coinUidIn) ?: coinCode(tx.coinUidIn)
                val coinCodeOut = PayCoreAssetResolver.coinCode(tx.coinUidOut) ?: coinCode(tx.coinUidOut)
                val status = tx.status.toStatus()
                val amountOutDisplay = tx.amountOutReal ?: tx.amountOut
                val price = formatPrice(tx.amountIn, coinCodeIn, amountOutDisplay, coinCodeOut)
                val priceInv = formatPrice(amountOutDisplay, coinCodeOut, tx.amountIn, coinCodeIn)

                uiState = PayCoreExchangeDetailUiState(
                    amountSentFormatted = numberFormatter.formatCoinFull(tx.amountIn, coinCodeIn, 8),
                    amountGotFormatted = numberFormatter.formatCoinFull(amountOutDisplay, coinCodeOut, 8),
                    coinImageUrlIn = coinImageUrl(tx.coinUidIn),
                    coinImageUrlOut = coinImageUrl(tx.coinUidOut),
                    priceFormatted = price,
                    priceInvFormatted = priceInv,
                    dateFormatted = formatDate(tx.date),
                    status = status,
                    transactionId = tx.transactionId,
                    supportUrl = AppConfigProvider.payCoreSupportUrl,
                )
            }
        }
    }

    private fun coinCode(uid: String) = marketKit.coin(uid)?.code ?: uid

    private fun formatPrice(
        amountFrom: BigDecimal,
        codeFrom: String,
        amountTo: BigDecimal,
        codeTo: String,
    ): String {
        if (amountFrom <= BigDecimal.ZERO) return ""
        val rate = amountTo.divide(amountFrom, 8, RoundingMode.HALF_EVEN).stripTrailingZeros()
        return "1 $codeFrom = ${rate.toPlainString()} $codeTo"
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

data class PayCoreExchangeDetailUiState(
    val amountSentFormatted: String,
    val amountGotFormatted: String,
    val coinImageUrlIn: String?,
    val coinImageUrlOut: String?,
    val priceFormatted: String,
    val priceInvFormatted: String,
    val dateFormatted: String,
    val status: TransactionStatusEnum,
    val transactionId: String,
    val supportUrl: String,
)
