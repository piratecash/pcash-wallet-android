package cash.p.terminal.modules.restoreaccount

import androidx.lifecycle.ViewModel
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig

class RestoreViewModel: ViewModel() {

    var accountType: AccountType? = null
        private set

    var accountName: String = ""
        private set

    var manualBackup: Boolean = false
        private set

    var fileBackup: Boolean = false
        private set

    // QR scan prefill data - shared across navigation
    var prefillWords: List<String>? = null
        private set
    var prefillPassphrase: String? = null
        private set
    var prefillMoneroHeight: Long? = null
        private set

    fun setPrefillData(
        words: List<String>?,
        passphrase: String?,
        moneroHeight: Long?
    ) {
        prefillWords = words
        prefillPassphrase = passphrase
        prefillMoneroHeight = moneroHeight
    }

    var tokenZCashConfig: TokenConfig? = null
        private set
    var tokenMoneroConfig: TokenConfig? = null
        private set
    var tokenZCashInitialConfig: TokenConfig? = null
        private set
    var tokenMoneroInitialConfig: TokenConfig? = null
        private set

    var cancelZCashConfig: Boolean = false
    var cancelMoneroConfig: Boolean = false

    fun setAccountData(accountType: AccountType, accountName: String, manualBackup: Boolean, fileBackup: Boolean) {
        this.accountType = accountType
        this.accountName = accountName
        this.manualBackup = manualBackup
        this.fileBackup = fileBackup
    }

    fun setZCashConfig(config: TokenConfig?) {
        tokenZCashConfig = config
    }

    fun setMoneroConfig(config: TokenConfig?) {
        tokenMoneroConfig = config
    }

    fun setZCashInitialConfig(config: TokenConfig?) {
        tokenZCashInitialConfig = config
    }

    fun setMoneroInitialConfig(config: TokenConfig?) {
        tokenMoneroInitialConfig = config
    }

}
