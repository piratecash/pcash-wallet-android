package cash.p.terminal.modules.importcexaccount

import androidx.lifecycle.viewModelScope
import com.binance.connector.client.exceptions.BinanceClientException
import com.binance.connector.client.exceptions.BinanceConnectorException
import com.google.gson.Gson
import cash.p.terminal.R
import cash.p.terminal.core.App
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.core.providers.BinanceCexProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EnterCexDataBinanceViewModel : ViewModelUiState<EnterCexDataBinanceViewModel.UiState>() {
    private val accountManager = App.accountManager
    private val accountFactory = App.accountFactory
    private val gson = Gson()

    private var apiKey: String? = null
    private var secretKey: String? = null
    private var accountCreated = false
    private var errorMessage: String? = null
    private var showSpinner = false

    override fun createState() = UiState(
        connectEnabled = !(apiKey.isNullOrBlank() || secretKey.isNullOrBlank()),
        accountCreated = accountCreated,
        apiKey = apiKey,
        secretKey = secretKey,
        errorMessage = errorMessage,
        showSpinner = showSpinner
    )

    fun onEnterApiKey(v: String) {
        apiKey = v
        emitState()
    }

    fun onEnterSecretKey(v: String) {
        secretKey = v
        emitState()
    }

    fun onScannedData(data: String) {
        val apiCredentials = try {
            gson.fromJson(data, BinanceCexApiCredentials::class.java)
        } catch (error: Throwable) {
            null
        }

        val scannedApiKey = apiCredentials?.apiKey
        val scannedSecretKey = apiCredentials?.secretKey
        if (scannedApiKey.isNullOrBlank() || scannedSecretKey.isNullOrBlank()) {
            apiKey = null
            secretKey = null

            errorMessage = cash.p.terminal.strings.helpers.Translator.getString(R.string.WalletConnect_Error_DataParsingError)
        } else {
            apiKey = scannedApiKey
            secretKey = scannedSecretKey

            errorMessage = null
        }

        emitState()
    }

    fun onClickConnect() {
        val tmpApiKey = apiKey ?: return
        val tmpSecretKey = secretKey ?: return
        showSpinner = true
        errorMessage = null
        emitState()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                BinanceCexProvider.validate(tmpApiKey, tmpSecretKey)
                createAccount(tmpApiKey, tmpSecretKey)
            } catch (error: BinanceClientException) {
                errorMessage = cash.p.terminal.strings.helpers.Translator.getString(R.string.Cex_Error_FailedToConnectApiKey)
            } catch (error: BinanceConnectorException) {
                errorMessage = cash.p.terminal.strings.helpers.Translator.getString(R.string.Hud_Text_NoInternet)
            }
            showSpinner = false
            emitState()
        }
    }

    private fun createAccount(binanceApiKey: String, binanceSecretKey: String) {
        val cexType = cash.p.terminal.wallet.CexType.Binance(binanceApiKey, binanceSecretKey)
        val name = accountFactory.getNextCexAccountName(cexType)

        val account = accountFactory.account(
            name,
            cash.p.terminal.wallet.AccountType.Cex(cexType = cexType),
            cash.p.terminal.wallet.AccountOrigin.Restored,
            true,
            false,
        )

        accountManager.save(account)

        accountCreated = true
    }

    data class BinanceCexApiCredentials(
        val apiKey: String?,
        val secretKey: String?,
        val comment: String?
    )

    class UiState(
        val connectEnabled: Boolean,
        val accountCreated: Boolean,
        val apiKey: String?,
        val secretKey: String?,
        val errorMessage: String?,
        val showSpinner: Boolean
    )
}
