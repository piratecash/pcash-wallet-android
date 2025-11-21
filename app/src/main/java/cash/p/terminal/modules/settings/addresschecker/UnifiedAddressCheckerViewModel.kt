package cash.p.terminal.modules.settings.addresschecker

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.address.AlphaAmlAddressValidator
import cash.p.terminal.core.address.ChainalysisAddressValidator
import cash.p.terminal.core.address.Eip20AddressValidator
import cash.p.terminal.core.address.HashDitAddressValidator
import cash.p.terminal.core.factories.ContractValidatorFactory
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressHandlerFactory
import cash.p.terminal.network.alphaaml.data.AlphaAmlRiskGrade
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.cancellation.CancellationException

class UnifiedAddressCheckerViewModel(
    private val marketKitWrapper: MarketKitWrapper,
    addressHandlerFactory: AddressHandlerFactory,
    private val hashDitValidator: HashDitAddressValidator,
    private val chainalysisValidator: ChainalysisAddressValidator,
    private val eip20Validator: Eip20AddressValidator,
    private val alphaAmlValidator: AlphaAmlAddressValidator
) : ViewModelUiState<AddressCheckState>() {
    private val checkPremiumUseCase: CheckPremiumUseCase by inject(CheckPremiumUseCase::class.java)

    private var addressValidationInProgress: Boolean = false

    private var checkResults: Map<IssueType, CheckState> = emptyMap()
    private var value = ""
    private var inputState: DataState<Address>? = null
    private var parseAddressJob: Job? = null
    private var currentRequestId = 0L

    companion object {
        private const val DEBOUNCE_DELAY_MS = 300L
    }

    private val parserChain = addressHandlerFactory.parserChain(null, true)
    private val coinUids = listOf("tether", "usd-coin", "paypal-usd")

    var hashDitBlockchains: List<Blockchain> = emptyList()
        private set
    var contractFullCoins: List<FullCoin> = emptyList()
    private var issueTypes: List<IssueType> = emptyList()

    init {
        viewModelScope.launch {
            hashDitBlockchains = try {
                val blockchains =
                    marketKitWrapper.blockchains(hashDitValidator.supportedBlockchainTypes.map { it.uid })
                hashDitValidator.supportedBlockchainTypes.mapNotNull { type ->
                    blockchains.firstOrNull { it.type == type }
                }
            } catch (e: Exception) {
                emptyList()
            }

            // Initialize contractFullCoins
            contractFullCoins = try {
                val fullCoins = marketKitWrapper.fullCoins(coinUids)
                coinUids.mapNotNull { uid ->
                    val fullCoin = fullCoins.firstOrNull { it.coin.uid == uid }
                    fullCoin?.let {
                        val filteredTokens = it.tokens.filter { token ->
                            eip20Validator.supports(token)
                        }
                        FullCoin(coin = it.coin, tokens = filteredTokens)
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }

            issueTypes = buildList {
                add(IssueType.SmartContract)
                add(IssueType.AlphaAml)
                add(IssueType.Chainalysis)
                addAll(hashDitBlockchains.map { IssueType.HashDit(it.type) })
                addAll(contractFullCoins.flatMap { it.tokens }.map { IssueType.Contract(it) })
            }

            checkResults = issueTypes.associateWith { CheckState.Idle }
        }
    }

    override fun createState() = AddressCheckState(
        value = value,
        inputState = inputState,
        addressValidationInProgress = addressValidationInProgress,
        checkResults = checkResults,
    )

    fun onEnterAddress(value: String) {
        parseAddressJob?.cancel()
        val requestId = ++currentRequestId

        this.value = value.trim()
        inputState = null

        if (value.isBlank()) {
            this.value = ""
            addressValidationInProgress = false
            resetCheckStatus()
        } else {
            addressValidationInProgress = true
            checkResults = issueTypes.associateWith { CheckState.Checking }
            emitState()

            parseAddressJob = viewModelScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                if (requestId == currentRequestId) {
                    processAddress(value, requestId)
                }
            }
        }
    }

    private suspend fun processAddress(address: String, requestId: Long) {
        try {
            if (requestId != currentRequestId) return

            val handlers = parserChain.supportedAddressHandlers(address)

            if (handlers.isEmpty()) {
                if (requestId != currentRequestId) return
                val error = Exception()
                inputState = DataState.Error(error)
                addressValidationInProgress = false
                emitState()
                return
            }

            val parsedAddress = handlers.first().parseAddress(address)
            check(parsedAddress, requestId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (requestId != currentRequestId) return
            inputState = DataState.Error(e)
            addressValidationInProgress = false
            emitState()
        }
    }

    private fun resetCheckStatus() {
        checkResults = issueTypes.associateWith { CheckState.Idle }
        emitState()
    }

    private suspend fun check(address: Address, requestId: Long) {
        withContext(Dispatchers.IO) {
            val checkJobs = issueTypes.map { type ->
                async {
                    type to performSingleCheck(address, type)
                }
            }

            try {
                checkJobs.forEach { deferred ->
                    val (type, result) = deferred.await()

                    // Skip if this is a stale request
                    if (requestId != currentRequestId) return@withContext

                    // Update UI incrementally
                    checkResults = checkResults.toMutableMap().apply {
                        this[type] = result
                    }
                    emitState()
                }
            } catch (e: CancellationException) {
                // Don't update state - new request already set its own state
                throw e
            } finally {
                // Only update if this is still the current request
                if (requestId == currentRequestId) {
                    addressValidationInProgress = false
                    emitState()
                }
            }
        }
    }

    private suspend fun checkOnSmartContract(addressStr: String): CheckState = coroutineScope {
        val handlers = parserChain.supportedAddressHandlers(addressStr)
        if (handlers.isEmpty()) return@coroutineScope CheckState.NotAvailable

        val results = handlers.map { handler ->
            async(Dispatchers.IO) {
                val address = tryOrNull { handler.parseAddress(addressStr) } ?: return@async null
                val blockchain = address.blockchainType ?: return@async null

                ContractValidatorFactory.get(blockchain)
                    ?.isContract(address.hex, blockchain) // true/false/null
            }
        }.awaitAll()

        if (results.any { it == null }) {
            throw IllegalStateException("No internet connection")
        }

        if (results.any { it == true }) {
            return@coroutineScope CheckState.Detected
        }

        return@coroutineScope CheckState.Clear
    }

    private suspend fun performSingleCheck(address: Address, type: IssueType): CheckState {
        val canCheck = when (type) {
            is IssueType.SmartContract,
            is IssueType.Chainalysis -> true

            is IssueType.AlphaAml,
            is IssueType.HashDit,
            is IssueType.Contract -> {
                address.blockchainType?.let { addressBlockchainType ->
                    EvmBlockchainManager.blockchainTypes.contains(addressBlockchainType)
                } ?: false
            }
        }

        if (!canCheck) {
            return CheckState.NotAvailable
        }

        return try {
            when (type) {
                is IssueType.SmartContract -> {
                    if (!checkPremiumUseCase.getPremiumType().isPremium()) {
                        throw PremiumNeededException()
                    }
                    checkOnSmartContract(address.hex)
                }

                is IssueType.AlphaAml -> {
                    val riskGrade = alphaAmlValidator.getRiskGrade(address)
                    when (riskGrade) {
                        null -> CheckState.NotAvailable
                        AlphaAmlRiskGrade.VeryLow -> CheckState.AlphaAmlVeryLow
                        AlphaAmlRiskGrade.Low -> CheckState.AlphaAmlLow
                        AlphaAmlRiskGrade.High -> CheckState.AlphaAmlHigh
                        AlphaAmlRiskGrade.VeryHigh -> CheckState.AlphaAmlVeryHigh
                    }
                }

                is IssueType.Chainalysis -> {
                    val isClear = chainalysisValidator.isClear(address)
                    if (isClear) CheckState.Clear else CheckState.Detected
                }

                is IssueType.HashDit -> {
                    val isClear = hashDitValidator.isClear(address, type.blockchainType)
                    if (isClear) CheckState.Clear else CheckState.Detected
                }

                is IssueType.Contract -> {
                    val isClear = eip20Validator.isClear(address, type.token)
                    if (isClear) CheckState.Clear else CheckState.Detected
                }
            }

        } catch (e: PremiumNeededException) {
            Log.e("TAG", "Premium needed for $type: ", e)
            CheckState.Locked
        } catch (e: Exception) {
            Log.e("TAG", "Single check error for $type: ", e)
            CheckState.NotAvailable
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val addressHandlerFactory = AddressHandlerFactory(AppConfigProvider.udnApiKey)
            val alphaAmlAddressValidator: AlphaAmlAddressValidator = getKoinInstance()
            return UnifiedAddressCheckerViewModel(
                App.marketKit,
                addressHandlerFactory,
                HashDitAddressValidator(
                    AppConfigProvider.hashDitBaseUrl,
                    AppConfigProvider.hashDitApiKey,
                    App.evmBlockchainManager
                ),
                ChainalysisAddressValidator(
                    AppConfigProvider.chainalysisBaseUrl,
                    AppConfigProvider.chainalysisApiKey
                ),
                Eip20AddressValidator(App.evmSyncSourceManager),
                alphaAmlAddressValidator
            ) as T
        }
    }
}

data class AddressCheckState(
    val value: String,
    val inputState: DataState<Address>?,
    val addressValidationInProgress: Boolean,
    val checkResults: Map<IssueType, CheckState>,
)

sealed class IssueType {
    object Chainalysis : IssueType()
    object SmartContract : IssueType()
    object AlphaAml : IssueType()
    data class HashDit(val blockchainType: BlockchainType) : IssueType()
    data class Contract(val token: Token) : IssueType()

    override fun equals(other: Any?): Boolean {
        return when {
            this is SmartContract && other is SmartContract -> true
            this is AlphaAml && other is AlphaAml -> true
            this is Chainalysis && other is Chainalysis -> true
            this is HashDit && other is HashDit -> this.blockchainType == other.blockchainType
            this is Contract && other is Contract -> this.token == other.token
            else -> false
        }
    }

    override fun hashCode(): Int {
        return when (this) {
            is SmartContract -> "smart_contract".hashCode()
            is AlphaAml -> "alpha_aml".hashCode()
            is Chainalysis -> "chainalysis".hashCode()
            is HashDit -> blockchainType.hashCode()
            is Contract -> token.hashCode()
        }
    }
}

enum class CheckState {
    Idle,
    Checking,
    Locked,
    Clear,
    Detected,
    NotAvailable,
    AlphaAmlVeryLow,
    AlphaAmlLow,
    AlphaAmlHigh,
    AlphaAmlVeryHigh;

    val title: Int
        get() = when (this) {
            Clear -> R.string.Send_Address_Error_Clear
            Detected -> R.string.Send_Address_Error_Detected
            NotAvailable -> R.string.NotAvailable
            AlphaAmlVeryLow -> R.string.alpha_aml_very_low_risk
            AlphaAmlLow -> R.string.alpha_aml_low_risk
            AlphaAmlHigh -> R.string.alpha_aml_high_risk
            AlphaAmlVeryHigh -> R.string.alpha_aml_very_high_risk
            else -> R.string.Idle
        }
}