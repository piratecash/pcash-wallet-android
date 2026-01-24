package cash.p.terminal.feature.miniapp.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.feature.miniapp.data.api.MiniAppApiException
import cash.p.terminal.feature.miniapp.data.api.PCashWalletRequestDto
import cash.p.terminal.feature.miniapp.data.api.Vector3DDto
import cash.p.terminal.feature.miniapp.data.api.toDto
import cash.p.terminal.feature.miniapp.domain.model.CoinType
import cash.p.terminal.feature.miniapp.domain.model.SpecialProposalData
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.feature.miniapp.domain.usecase.CaptchaUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.CheckIfEmulatorUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.CheckRequiredTokensUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.CollectDeviceEnvironmentUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.CreateRequiredTokensUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.GetPirateJettonAddressUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.GetSpecialProposalDataUseCase
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.GetBnbAddressUseCase
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.BuildConfig
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.balance.BalanceService
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ConnectMiniAppViewModel(
    private val checkIfEmulatorUseCase: CheckIfEmulatorUseCase,
    private val collectDeviceEnvironmentUseCase: CollectDeviceEnvironmentUseCase,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val captchaUseCase: CaptchaUseCase,
    private val getSpecialProposalDataUseCase: GetSpecialProposalDataUseCase,
    private val checkRequiredTokensUseCase: CheckRequiredTokensUseCase,
    private val createRequiredTokensUseCase: CreateRequiredTokensUseCase,
    private val getPirateJettonAddressUseCase: GetPirateJettonAddressUseCase,
    private val accountManager: IAccountManager,
    private val marketKitWrapper: MarketKitWrapper,
    private val balanceService: BalanceService,
    private val getBnbAddressUseCase: GetBnbAddressUseCase,
    private val uniqueCodeStorage: IUniqueCodeStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val STEP_WALLET = 1
        const val STEP_TERMS = 2
        const val STEP_CAPTCHA = 3
        const val STEP_SPECIAL_PROPOSAL = 4
        const val STEP_FINISH = 5
    }

    private fun Token.nameWithBadge(): String {
        val badgeText = badge
        return if (badgeText != null) "${coin.name} $badgeText" else coin.name
    }

    private val input: ConnectMiniAppDeeplinkInput? = savedStateHandle["input"]
    val jwt: String? = input?.jwt
    val endpoint: String = input?.endpoint ?: "https://p.cash/"

    var uiState by mutableStateOf(ConnectMiniAppUiState())
        private set

    init {
        viewModelScope.launch {
            accountManager.accountsFlow.collect {
                checkWalletStatus()
            }
        }
        checkWalletStatus()
    }

    fun checkWalletStatus() {
        if (uiState.currentStep > STEP_WALLET) {
            return
        }

        uiState.chosenAccountId?.let { chosenAccountId ->
            // Already have selected account
            viewModelScope.launch {
                val account = accountManager.account(chosenAccountId)
                checkSingleWallet(account)
            }
            return
        }

        val eligibleAccounts = getEligibleAccountsToChoose()
        when {
            eligibleAccounts.size > 1 -> checkWalletsMultiple(eligibleAccounts)
            eligibleAccounts.isEmpty() -> {
                uiState = uiState.copy(
                    currentStep = STEP_WALLET,
                    isLoading = false,
                    needsBackup = false,
                    walletItems = emptyList(),
                    chosenAccountId = null
                )
            }

            else -> checkSingleWallet(eligibleAccounts.firstOrNull())
        }
    }

    private fun checkWalletsMultiple(eligibleAccounts: List<Account>) {
        viewModelScope.launch {
            val walletItems = eligibleAccounts.map { account ->
                WalletViewItem(
                    accountId = account.id,
                    name = account.name,
                    isPremium = checkPremiumUseCase.checkPremiumByBalanceForAccount(account)
                        .isPremium()
                )
            }
            uiState = uiState.copy(
                walletItems = walletItems,
                isLoading = false,
                preselectedAccountId = uniqueCodeStorage.connectedAccountId.takeIf { it.isNotBlank() }
                    ?: walletItems.firstOrNull { it.isPremium }?.accountId
                    ?: walletItems.firstOrNull()?.accountId
            )
        }
    }

    private fun checkSingleWallet(account: Account?) {
        if (account == null) {
            uiState = uiState.copy(
                currentStep = STEP_WALLET,
                needsBackup = false,
                walletItems = emptyList(),
                isLoading = false,
                chosenAccountId = null
            )
            return
        }

        if (account.hasAnyBackup) {
            // Set active account and check token availability
            accountManager.setActiveAccountId(account.id)
            uiState = uiState.copy(
                isLoading = false,
                chosenAccountId = account.id
            )
            checkTokenAvailability()
        } else {
            uiState = uiState.copy(
                currentStep = STEP_WALLET,
                isLoading = false,
                needsBackup = true,
                chosenAccountId = account.id
            )
        }
    }

    private fun getEligibleAccountsToChoose(): List<Account> {
        return accountManager.accounts.filter { account ->
            !account.isWatchAccount && (account.type.canAddTokens || account.type is AccountType.HardwareCard)
        }
    }

    fun onWalletSelected(accountId: String) {
        uiState = uiState.copy(preselectedAccountId = accountId)
    }

    fun onConfirmWalletSelectedClick() {
        uiState.preselectedAccountId?.let { accountId ->
            uiState = uiState.copy(chosenAccountId = accountId)
            accountManager.setActiveAccountId(accountId)
        }
        checkTokenAvailability()
    }

    private fun checkTokenAvailability() {
        val accountId = uiState.chosenAccountId ?: return
        val account = accountManager.account(accountId) ?: return

        if (!account.hasAnyBackup && account.supportsBackup) { // Waiting for a backup
            checkWalletStatus()
            return
        }

        uiState = uiState.copy(isCheckingTokens = true, missingTokenNames = emptyList())

        viewModelScope.launch {
            runCatching {
                checkRequiredTokensUseCase(account)
            }.onSuccess { result ->
                val allTokensText = result.allTokens.joinToString(", ") { it.nameWithBadge() }
                if (result.allTokensExist) {
                    uiState = uiState.copy(
                        isCheckingTokens = false,
                        allTokensText = allTokensText,
                        currentStep = STEP_TERMS
                    )
                } else {
                    val missingNames = result.missingTokens.map { it.nameWithBadge() }
                    uiState = uiState.copy(
                        isCheckingTokens = false,
                        allTokensText = allTokensText,
                        missingTokenNames = missingNames,
                        missingTokenQueries = result.missingTokenQueries
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "Failed to check token availability")
                uiState = uiState.copy(
                    isCheckingTokens = false,
                    currentStep = STEP_TERMS // Proceed even on error
                )
            }
        }
    }

    fun onAddTokensClick() {
        val accountId = uiState.chosenAccountId ?: return
        val account = accountManager.account(accountId) ?: return
        val missingQueries = uiState.missingTokenQueries

        if (missingQueries.isEmpty()) return

        uiState = uiState.copy(isAddingTokens = true)

        viewModelScope.launch {
            runCatching {
                createRequiredTokensUseCase(account, missingQueries)
            }.onSuccess {
                uiState = uiState.copy(isAddingTokens = false)
                checkTokenAvailability() // Re-check after creation
            }.onFailure { error ->
                Timber.e(error, "Failed to add tokens")
                uiState = uiState.copy(isAddingTokens = false)
            }
        }
    }

    fun onTermsAgreedChange(agreed: Boolean) {
        uiState = uiState.copy(termsAgreed = agreed)
    }

    fun onTermsAccepted() {
        if (uiState.termsAgreed) {
            collectDeviceEnvironmentUseCase.startCollection()
            uiState = uiState.copy(currentStep = STEP_CAPTCHA)
            loadCaptcha()
        }
    }

    fun onBackToWalletSelection() {
        uiState = uiState.copy(currentStep = STEP_WALLET, termsAgreed = false)
    }

    // Captcha methods
    fun loadCaptcha() {
        val currentJwt = jwt ?: return

        uiState = uiState.copy(
            isCaptchaLoading = true,
            captchaError = null,
            captchaCode = ""
        )

        viewModelScope.launch {
            captchaUseCase.getCaptcha(currentJwt, endpoint)
                .onSuccess { response ->
                    uiState = uiState.copy(
                        captchaImageBase64 = response.imageBase64,
                        captchaExpiresIn = response.expiresIn,
                        isCaptchaLoading = false
                    )
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load captcha")
                    val errorMessage = when (error) {
                        is MiniAppApiException -> error.message
                        else -> "Failed to load captcha"
                    }
                    uiState = uiState.copy(
                        isCaptchaLoading = false,
                        captchaError = errorMessage
                    )
                }
        }
    }

    fun onCaptchaCodeChange(code: String) {
        uiState = uiState.copy(
            captchaCode = code,
            captchaError = null // Clear error when user types
        )
    }

    fun refreshCaptcha() {
        loadCaptcha()
    }

    fun verifyCaptcha() {
        val currentJwt = jwt ?: return
        val code = uiState.captchaCode

        if (code.length != 5) return

        uiState = uiState.copy(isCaptchaVerifying = true, captchaError = null)

        viewModelScope.launch {
            captchaUseCase.verifyCaptcha(currentJwt, endpoint, code)
                .onSuccess { response ->
                    if (response.valid) {
                        uiState = uiState.copy(isCaptchaVerifying = false)
                        checkPremiumAndProceed()
                    } else {
                        // Wrong code
                        uiState = uiState.copy(
                            isCaptchaVerifying = false,
                            captchaError = "Wrong code, please try again"
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to verify captcha")
                    val errorMessage = when (error) {
                        is MiniAppApiException -> error.message
                        else -> "Verification failed, please try again"
                    }
                    uiState = uiState.copy(
                        isCaptchaVerifying = false,
                        captchaError = errorMessage
                    )
                }
        }
    }

    private fun checkPremiumAndProceed() {
        val accountId = uiState.chosenAccountId ?: return
        val account = accountManager.account(accountId) ?: return

        viewModelScope.launch {
            val premiumType = checkPremiumUseCase.checkPremiumByBalanceForAccount(
                account = account,
                checkTrial = false
            )
            if (premiumType == PremiumType.COSA || premiumType == PremiumType.PIRATE) {
                // Premium user - skip special proposal, connect directly
                connectWallet()
            } else {
                // Non-premium user - show special proposal
                uiState = uiState.copy(currentStep = STEP_SPECIAL_PROPOSAL)
                loadSpecialProposalData()
            }
        }
    }

    // Step 5 - Special Proposal methods
    fun loadSpecialProposalData() {
        val currentJwt = jwt ?: return
        val selectedAccountId = uiState.chosenAccountId ?: return

        uiState = uiState.copy(isSpecialProposalLoading = true, specialProposalError = null)

        viewModelScope.launch {
            runCatching {
                getSpecialProposalDataUseCase(
                    selectedAccountId = selectedAccountId,
                    jwt = currentJwt,
                    endpoint = endpoint,
                    currencyCode = balanceService.baseCurrency.code
                )
            }.onSuccess { data ->
                uiState = uiState.copy(
                    specialProposalData = data,
                    selectedCoinTab = data.cheaperOption,
                    isPremiumUser = data.hasPremium,
                    isSpecialProposalLoading = false
                )
            }.onFailure { error ->
                Timber.e(error, "Failed to load special proposal data")
                val errorMessage = when (error) {
                    is MiniAppApiException -> error.message
                    else -> "Failed to load data"
                }
                uiState = uiState.copy(
                    isSpecialProposalLoading = false,
                    specialProposalError = errorMessage
                )
            }
        }
    }

    fun onCoinTabSelected(coinType: CoinType) {
        uiState = uiState.copy(selectedCoinTab = coinType)
    }

    suspend fun getTokenForSwap(): Token? {
        val contractAddress = when (uiState.selectedCoinTab) {
            CoinType.PIRATE -> BuildConfig.PIRATE_CONTRACT
            CoinType.COSA -> BuildConfig.COSANTA_CONTRACT
        }

        val tokenQuery = TokenQuery.eip20(BlockchainType.BinanceSmartChain, contractAddress)
        return withContext(Dispatchers.IO) {
            marketKitWrapper.token(tokenQuery)
        }
    }

    fun connectWallet() {
        val currentJwt = jwt ?: return
        val accountId = uiState.chosenAccountId ?: return

        uiState = uiState.copy(currentStep = STEP_FINISH, finishState = FinishState.Loading)

        viewModelScope.launch {
            runCatching {
                val account = accountManager.account(accountId)
                    ?: throw IllegalStateException("Account not found")
                val deviceEnv = collectDeviceEnvironmentUseCase.stopCollection()

                // Get Pirate JETTON wallet address (this is the wallet address to send to API)
                val pirateJettonAddress = getPirateJettonAddressUseCase.getAddress(account)
                    ?: throw IllegalStateException("Pirate JETTON wallet address not found")

                // Get EVM address
                val evmAddress = getBnbAddressUseCase.getAddress(account)
                    ?: throw IllegalStateException("EVM address not found")

                val emulatorResult = checkIfEmulatorUseCase()
                val isEmulator = emulatorResult.isEmulator

                val request = PCashWalletRequestDto(
                    walletAddress = pirateJettonAddress,
                    premiumAddress = evmAddress,
                    pirate = uiState.specialProposalData?.pirateBalance?.toPlainString() ?: "0",
                    cosa = uiState.specialProposalData?.cosaBalance?.toPlainString() ?: "0",
                    uniqueCode = uniqueCodeStorage.uniqueCode.ifBlank { null },
                    gyro = deviceEnv.gyroscopeAverage?.toDto() ?: Vector3DDto.ZERO,
                    accelerometer = deviceEnv.accelerometerAverage?.toDto() ?: Vector3DDto.ZERO,
                    gyroVariance = deviceEnv.gyroscopeVariance?.toDto() ?: Vector3DDto.ZERO,
                    accelerometerVariance = deviceEnv.accelerometerVariance?.toDto()
                        ?: Vector3DDto.ZERO,
                    batteryPercent = deviceEnv.batteryLevel,
                    isCharging = deviceEnv.isCharging,
                    chargingType = deviceEnv.chargingType.name,
                    isUsbConnected = deviceEnv.isUsbConnected,
                    deviceModel = deviceEnv.deviceModel,
                    osVersion = deviceEnv.osVersion,
                    sdkVersion = deviceEnv.sdkVersion,
                    hasGyroscope = deviceEnv.hasGyroscope,
                    hasAccelerometer = deviceEnv.hasAccelerometer,
                    emulator = isEmulator,
                    isMoving = deviceEnv.wasDeviceMoved,
                    isHandHeld = deviceEnv.isHandHeld,
                    isDev = deviceEnv.isDeveloperOptionsEnabled,
                    isAdb = deviceEnv.isAdbEnabled,
                    isRooted = deviceEnv.isRooted,
                    collectionDurationMs = deviceEnv.collectionDurationMs,
                    sampleCount = deviceEnv.sampleCount
                )

                captchaUseCase.submitPCashWallet(currentJwt, endpoint, request).getOrThrow()
            }.onSuccess { response ->
                // Save returned uniqueCode to storage
                uniqueCodeStorage.connectedAccountId = accountId
                uniqueCodeStorage.uniqueCode = response.uniqueCode.orEmpty()
                uiState = uiState.copy(finishState = FinishState.Success)
            }.onFailure { error ->
                Timber.e(error, "Failed to submit pcash wallet")
                val errorMessage = when (error) {
                    is MiniAppApiException -> error.message
                    else -> error.message ?: "Connection failed"
                }
                uiState = uiState.copy(finishState = FinishState.Error(errorMessage))
            }
        }
    }

    fun onRetryClick() {
        connectWallet()
    }

    fun onFinishClose() {
        uiState = uiState.copy(closeEvent = true)
    }

    override fun onCleared() {
        collectDeviceEnvironmentUseCase.stopCollection()
    }
}

sealed class FinishState {
    data object Loading : FinishState()
    data object Success : FinishState()
    data class Error(val message: String?) : FinishState()
}

data class ConnectMiniAppUiState(
    val currentStep: Int = 1,
    val isEmulator: Boolean = false,
    val needsBackup: Boolean = false,
    val isLoading: Boolean = true,
    val walletItems: List<WalletViewItem> = emptyList(),
    val chosenAccountId: String? = null,
    // for UI selection only
    val preselectedAccountId: String? = null,
    val termsAgreed: Boolean = false,
    // Token checking state
    val isCheckingTokens: Boolean = false,
    val allTokensText: String = "",
    val missingTokenNames: List<String> = emptyList(),
    val missingTokenQueries: List<TokenQuery> = emptyList(),
    val isAddingTokens: Boolean = false,
    // Captcha state
    val captchaImageBase64: String? = null,
    val captchaExpiresIn: Long = 0,
    val captchaCode: String = "",
    val captchaError: String? = null,
    val isCaptchaLoading: Boolean = false,
    val isCaptchaVerifying: Boolean = false,
    // Step 5 - Special Proposal state
    val specialProposalData: SpecialProposalData? = null,
    val selectedCoinTab: CoinType = CoinType.PIRATE,
    val isSpecialProposalLoading: Boolean = false,
    val isPremiumUser: Boolean = false,
    val specialProposalError: String? = null,
    // Finish state
    val finishState: FinishState? = null,
    val closeEvent: Boolean = false
)

data class WalletViewItem(
    val accountId: String,
    val name: String,
    val isPremium: Boolean
)
