package cash.p.terminal.modules.importcexaccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.core.App
import cash.p.terminal.entities.AccountOrigin
import cash.p.terminal.entities.AccountType
import cash.p.terminal.entities.CexType

class EnterCexDataBinanceViewModel : ViewModel() {
    private val accountManager = App.accountManager
    private val accountFactory = App.accountFactory

    private var apiKey: String? = null
    private var secretKey: String? = null

    var connectEnabled by mutableStateOf(false)
        private set
    var accountCreated by mutableStateOf(false)
        private set

    fun onEnterApiKey(v: String) {
        apiKey = v
        emitState()
    }

    fun onEnterSecretKey(v: String) {
        secretKey = v
        emitState()
    }

    private fun emitState() {
        connectEnabled = !(apiKey.isNullOrBlank() || secretKey.isNullOrBlank())
    }

    fun onClickConnect() {
        val tmpApiKey = apiKey ?: return
        val tmpSecretKey = secretKey ?: return
        val cexType = CexType.Binance(tmpApiKey, tmpSecretKey)

        val account = accountFactory.account("Binance Wallet", AccountType.Cex(cexType = cexType), AccountOrigin.Restored, true, false)

        accountManager.save(account)

        accountCreated = true
    }

}