package cash.p.terminal.modules.tonconnect

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.toTonWalletFullAccess
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class TonConnectNewViewModel(
    private val requestEntity: DAppRequestEntity,
) : ViewModelUiState<TonConnectNewUiState>() {

    private val hardwarePublicKeyStorage: HardwarePublicKeyStorage by inject(
        HardwarePublicKeyStorage::class.java
    )

    private val tonConnectKit = App.tonConnectManager.kit

    private var manifest: DAppManifestEntity? = null
    private var accounts: List<Account> = listOf()
    private var account = App.accountManager.activeAccount
    private var finish = false
    private var error: Throwable? = null
    private var toast: String? = null

    override fun createState() = TonConnectNewUiState(
        manifest = manifest,
        accounts = accounts,
        account = account,
        finish = finish,
        error = error,
        toast = toast,
    )

    init {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                manifest = tonConnectKit.getManifest(requestEntity.payload.manifestUrl)
                emitState()
            } catch (e: Throwable) {
                error = NoManifestError()
                emitState()
            }
        }

        accounts = App.accountManager.accounts.filter {
            it.type is AccountType.Mnemonic ||
                    it.type is AccountType.HardwareCard
        }

        if (accounts.isEmpty()) {
            error = NoTonAccountError()
            emitState()
        }
    }

    fun onSelectAccount(account: Account) {
        this.account = account
        emitState()
    }

    fun connect() {
        viewModelScope.launch {
            try {
                val manifest = manifest ?: throw NoManifestError()
                val account = account ?: throw IllegalArgumentException("Empty account")

                val res = tonConnectKit.connect(
                    requestEntity,
                    manifest,
                    account.id,
                    account.toTonWalletFullAccess(
                        hardwarePublicKeyStorage,
                        BlockchainType.Ton,
                        TokenType.Native
                    )
                )
                println("TonConnect connect result: $res")
                finish = true
            } catch (e: Throwable) {
                toast = e.message?.nullIfBlank() ?: e.javaClass.simpleName
            }

            emitState()
        }
    }

    fun reject() {
        finish = true
        emitState()
    }

    fun onToastShow() {
        toast = null
        emitState()
    }
}

sealed class TonConnectError : Error()
class NoManifestError : TonConnectError()
class NoTonAccountError : TonConnectError()

data class TonConnectNewUiState(
    val manifest: DAppManifestEntity?,
    val accounts: List<Account>,
    val account: Account?,
    val finish: Boolean,
    val error: Throwable?,
    val toast: String?
) {
    val connectEnabled get() = error == null && account != null
}
