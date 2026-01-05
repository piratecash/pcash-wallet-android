package cash.p.terminal.modules.premium.smsnotification

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAddressValidator
import cash.p.terminal.feature.logging.domain.usecase.GetZecWalletsUseCase
import cash.p.terminal.modules.pin.SendZecOnDuressUseCase
import cash.p.terminal.modules.pin.SendZecResult
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.ISmsNotificationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class SendSmsNotificationViewModel(
    private val smsNotificationSettings: ISmsNotificationSettings,
    private val getZecWalletsUseCase: GetZecWalletsUseCase,
    private val adapterManager: IAdapterManager,
    private val userManager: UserManager,
    private val sendZecOnDuressUseCase: SendZecOnDuressUseCase
) : ViewModel() {

    private val currentLevel: Int
        get() = userManager.getUserLevel()

    private var testTransactionJob: Job? = null

    // Track original saved values to detect changes
    private var savedAccountId: String? = null
    private var savedAddress: String = ""
    private var savedMemo: String = ""

    var uiState by mutableStateOf(
        SendSmsNotificationUiState(
            requiredCoins = ISmsNotificationSettings.AMOUNT_TO_SEND_ZEC.add(ZcashAdapter.MINERS_FEE)
        )
    )
        private set

    init {
        loadSavedSettings()
        loadAvailableWallets()
    }

    private fun loadAvailableWallets() {
        viewModelScope.launch {
            val wallets = getZecWalletsUseCase.getZecWallets()
            val walletItems = wallets.map { wallet ->
                WalletViewItem(
                    accountId = wallet.account.id,
                    accountName = wallet.account.name,
                    coinCode = wallet.coin.code,
                    wallet = wallet
                )
            }

            // Extract unique accounts for bottom sheet display
            val accounts = wallets
                .map { it.account }
                .distinctBy { it.id }

            uiState = uiState.copy(
                availableWallets = walletItems,
                availableAccounts = accounts
            )

            // If we have a saved wallet key, select it
            smsNotificationSettings.getSmsNotificationAccountId(currentLevel)
                ?.let { savedWalletKey ->
                    walletItems.find { it.accountId == savedWalletKey }?.let {
                        selectWallet(it)
                    }
                }
        }
    }

    private fun loadSavedSettings() {
        savedAccountId = smsNotificationSettings.getSmsNotificationAccountId(currentLevel)
        savedAddress = smsNotificationSettings.getSmsNotificationAddress(currentLevel) ?: ""
        savedMemo = smsNotificationSettings.getSmsNotificationMemo(currentLevel) ?: ""

        uiState = uiState.copy(
            address = savedAddress,
            memo = savedMemo,
            memoBytesUsed = savedMemo.toByteArray(Charsets.UTF_8).size
        )
        updateButtonStates()
    }

    private fun selectWallet(walletItem: WalletViewItem) {
        val adapter: ISendZcashAdapter? = adapterManager.getAdapterForWallet(walletItem.wallet)
        // Use adapter fee if available, otherwise use default Zcash miners fee from SDK
        val fee = adapter?.fee?.value ?: ZcashAdapter.MINERS_FEE

        uiState = uiState.copy(
            selectedWallet = walletItem,
            requiredCoins = ISmsNotificationSettings.AMOUNT_TO_SEND_ZEC.add(fee)
        )
        updateButtonStates()
    }

    fun onAccountSelected(account: Account) {
        // Find the first WalletViewItem for this account
        val walletItem = uiState.availableWallets.find { it.wallet.account.id == account.id }
        if (walletItem != null) {
            selectWallet(walletItem)
        }
    }

    fun onAddressChanged(address: String) {
        uiState = uiState.copy(
            address = address,
            addressError = null
        )
        validateAddress(address)
        updateButtonStates()
    }

    fun onMemoChanged(memo: String) {
        val bytes = memo.toByteArray(Charsets.UTF_8)
        if (bytes.size <= SendSmsNotificationUiState.MAX_MEMO_BYTES) {
            uiState = uiState.copy(
                memo = memo,
                memoBytesUsed = bytes.size
            )
            updateButtonStates()
        }
    }

    private fun validateAddress(address: String) {
        if (address.isBlank()) {
            uiState = uiState.copy(addressError = null)
            return
        }

        // First check if it's a valid Zcash address format
        val isValid = ZcashAddressValidator.validate(address)
        if (!isValid) {
            uiState = uiState.copy(
                addressError = Exception(Translator.getString(R.string.SwapSettings_Error_InvalidAddress))
            )
            updateButtonStates()
            return
        }

        // Reject transparent addresses - only shielded addresses are allowed
        if (ZcashAddressValidator.isTransparentAddress(address)) {
            uiState = uiState.copy(
                addressError = Exception(Translator.getString(R.string.send_sms_transparent_address_not_allowed))
            )
            updateButtonStates()
            return
        }

        uiState = uiState.copy(addressError = null)
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val hasWallet = uiState.selectedWallet != null
        val hasValidAddress = uiState.address.isNotBlank() && uiState.addressError == null

        uiState = uiState.copy(
            isSaveEnabled = hasWallet && hasValidAddress,
            isTestEnabled = hasWallet && hasValidAddress
        )
    }

    fun onSaveClick() {
        val accountId = uiState.selectedWallet?.accountId ?: return
        val address = uiState.address
        val memo = uiState.memo

        smsNotificationSettings.setSmsNotificationAccountId(currentLevel, accountId)
        smsNotificationSettings.setSmsNotificationAddress(currentLevel, address)
        smsNotificationSettings.setSmsNotificationMemo(currentLevel, memo)

        // Update saved values to reflect new state
        savedAccountId = accountId
        savedAddress = address
        savedMemo = memo

        uiState = uiState.copy(saveSuccess = true)
        updateButtonStates()
    }

    fun onSaveSuccessShown() {
        uiState = uiState.copy(saveSuccess = false)
    }

    fun onTestSmsClick() {
        val wallet = uiState.selectedWallet?.wallet ?: return
        val address = uiState.address
        val memo = uiState.memo

        testTransactionJob = viewModelScope.launch {
            uiState = uiState.copy(testResult = TestResult.Syncing)

            val result = withContext(Dispatchers.IO) {
                sendZecOnDuressUseCase.sendTestTransaction(wallet, address, memo)
            }

            uiState = uiState.copy(
                testResult = when (result) {
                    is SendZecResult.Success -> TestResult.Success
                    is SendZecResult.InsufficientBalance -> TestResult.InsufficientBalance
                    is SendZecResult.WalletNotFound -> TestResult.Failed("Wallet not found")
                    is SendZecResult.AdapterCreationFailed -> TestResult.Failed("Failed to create adapter")
                    is SendZecResult.TransactionFailed -> TestResult.Failed(result.error)
                }
            )
        }
    }

    fun cancelTestTransaction() {
        testTransactionJob?.cancel()
        testTransactionJob = null
        if (uiState.testResult is TestResult.Syncing) {
            uiState = uiState.copy(testResult = null)
        }
    }

    fun onTestResultShown() {
        uiState = uiState.copy(testResult = null)
    }
}

data class SendSmsNotificationUiState(
    val selectedWallet: WalletViewItem? = null,
    val availableWallets: List<WalletViewItem> = emptyList(),
    val availableAccounts: List<Account> = emptyList(),
    val address: String = "",
    val addressError: Throwable? = null,
    val memo: String = "",
    val memoBytesUsed: Int = 0,
    val requiredCoins: BigDecimal = BigDecimal.ZERO,
    val isSaveEnabled: Boolean = false,
    val isTestEnabled: Boolean = false,
    val saveSuccess: Boolean = false,
    val testResult: TestResult? = null
) {
    val selectedAccount: Account?
        get() = selectedWallet?.wallet?.account

    companion object {
        const val MAX_MEMO_BYTES = 512
    }
}

data class WalletViewItem(
    val accountId: String,
    val accountName: String,
    val coinCode: String,
    val wallet: Wallet
)

sealed class TestResult {
    data object Syncing : TestResult()
    data object Success : TestResult()
    data object InsufficientBalance : TestResult()
    data class Failed(val error: String) : TestResult()
}
