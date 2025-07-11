package cash.p.terminal.modules.multiswap.sendtransaction

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.ServiceState
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.send.SendModule
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import java.net.UnknownHostException

abstract class ISendTransactionService<T>(protected val token: Token) :
    ServiceState<SendTransactionServiceState>() {

    private val walletUseCase: WalletUseCase by inject(WalletUseCase::class.java)
    protected val wallet: Wallet by lazy { runBlocking { walletUseCase.createWalletIfNotExists(token)!! } }
    protected val adapterManager: IAdapterManager by inject(IAdapterManager::class.java)
    protected val adapter = (adapterManager.getAdapterForWallet(wallet) as T)
    protected val rate: CurrencyValue?
        get() {
            val currencyManager: CurrencyManager by inject(CurrencyManager::class.java)
            val marketKit: MarketKitWrapper by inject(MarketKitWrapper::class.java)
            val baseCurrency = currencyManager.baseCurrency
            return marketKit.coinPrice(token.coin.uid, baseCurrency.code)?.let {
                CurrencyValue(baseCurrency, it.value)
            }
        }

    protected val coroutineScope = CoroutineScope(Dispatchers.IO)

    abstract fun start(coroutineScope: CoroutineScope)
    abstract fun setSendTransactionData(data: SendTransactionData)

    @Composable
    abstract fun GetSettingsContent(navController: NavController)

    abstract suspend fun sendTransaction(): SendTransactionResult
    abstract val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings>

    private inline fun <reified T> getAdapterType() = T::class.java

    protected fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> CautionViewItem(
            TranslatableString.ResString(R.string.Hud_Text_NoInternet).toString(),
            "",
            CautionViewItem.Type.Error
        )

        is LocalizedException -> CautionViewItem(
            TranslatableString.ResString(error.errorTextRes).toString(),
            "",
            CautionViewItem.Type.Error
        )

        else -> CautionViewItem(
            TranslatableString.PlainString(error.message ?: "").toString(),
            "",
            CautionViewItem.Type.Error
        )
    }
}

data class SendTransactionServiceState(
    val availableBalance: BigDecimal?,
    val networkFee: SendModule.AmountData?,
    val cautions: List<CautionViewItem>,
    val sendable: Boolean,
    val loading: Boolean,
    val fields: List<DataField>
)
