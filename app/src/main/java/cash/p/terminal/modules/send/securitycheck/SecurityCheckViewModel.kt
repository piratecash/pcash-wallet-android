package cash.p.terminal.modules.send.securitycheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.address.AddressCheckManager
import cash.p.terminal.core.address.AddressCheckType
import cash.p.terminal.core.factories.AddressValidatorFactory
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressHandlerEns
import cash.p.terminal.modules.address.AddressHandlerUdn
import cash.p.terminal.modules.address.AddressParserChain
import cash.p.terminal.modules.address.EnsResolverHolder
import cash.p.terminal.modules.send.address.AddressCheckResult
import cash.p.terminal.modules.send.address.AddressExtractor
import cash.p.terminal.modules.send.address.EnterAddressValidator
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class SecurityCheckViewModel(
    private val token: Token,
    addressUriParser: AddressUriParser,
    initialAddress: String,
    private val localStorage: ILocalStorage,
    private val addressCheckerSkippable: Boolean,
    private val domainParser: AddressParserChain,
    private val addressValidator: EnterAddressValidator,
    private val addressCheckManager: AddressCheckManager,
) : ViewModelUiState<SecurityCheckUiState>() {
    private val checkPremiumUseCase: CheckPremiumUseCase by inject(CheckPremiumUseCase::class.java)

    private var address: Address? = null
    private val canBeSendToAddress: Boolean
        get() = address != null && !addressValidationInProgress && addressValidationError == null
    private var addressValidationInProgress: Boolean = true
    private var addressValidationError: Throwable? = null

    private var checkResults: Map<AddressCheckType, AddressCheckData> = mapOf()
    private var value = ""
    private var inputState: DataState<Address>? = null

    private val addressExtractor = AddressExtractor(token.blockchainType, addressUriParser)
    private val addressCheckByBaseEnabled: Boolean
        get() = if (addressCheckerSkippable) {
            localStorage.recipientAddressBaseCheckEnabled
        } else {
            true
        }
    private val addressCheckByContractEnabled: Boolean
        get() = if (addressCheckerSkippable) {
            localStorage.recipientAddressContractCheckEnabled && checkPremiumUseCase.getPremiumType()
                .isPremium()
        } else {
            true
        }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            checkAddress(initialAddress)
        }
    }

    override fun createState() = SecurityCheckUiState(
        canBeSendToAddress = canBeSendToAddress,
        value = value,
        inputState = inputState,
        address = address,
        addressValidationInProgress = addressValidationInProgress,
        addressValidationError = addressValidationError,
        checkResults = checkResults,
    )

    private suspend fun checkAddress(value: String) {
        if (!value.isBlank()) {
            try {
                val addressString = addressExtractor.extractAddressFromUri(value.trim())
                this@SecurityCheckViewModel.value = addressString
                emitState()

                processAddress(addressString)
            } catch (e: Throwable) {
                inputState = DataState.Error(e)
                emitState()
            }
        }
    }

    private fun getAvailableCheckTypes(): List<AddressCheckType> {
        if (!addressCheckByBaseEnabled && !addressCheckByContractEnabled) return emptyList()

        return addressCheckManager.availableCheckTypes(token).filter {
            (addressCheckByContractEnabled && it == AddressCheckType.SmartContract) ||
                    (addressCheckByBaseEnabled && it != AddressCheckType.SmartContract) ||
                    (it.isPremiumRequired() && !checkPremiumUseCase.getPremiumType()
                        .isPremium())// To promote in list
        }
    }

    private suspend fun processAddress(addressText: String) {
        try {
            val address = parseDomain(addressText)
            try {
                withContext(Dispatchers.IO) {
                    addressValidator.validate(address)
                }

                this@SecurityCheckViewModel.address = address
                addressValidationInProgress = false
                addressValidationError = null

                emitState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Throwable) {

                this@SecurityCheckViewModel.address = null
                addressValidationInProgress = false
                addressValidationError = e

                emitState()
            }

            if (addressValidationError != null) {
                checkResults = mapOf()
                emitState()
            } else {
                val availableCheckTypes = getAvailableCheckTypes()
                checkResults = availableCheckTypes.associateWith { AddressCheckData(true) }
                emitState()

                withContext(Dispatchers.IO) {
                    availableCheckTypes.forEach { type ->
                        val checkResult = try {
                            if (type.isPremiumRequired() &&
                                !checkPremiumUseCase.getPremiumType().isPremium()
                            ) {
                                AddressCheckResult.NotAllowed
                            } else {
                                addressCheckManager.isClear(type, address, token)
                            }
                        } catch (e: Throwable) {
                            AddressCheckResult.NotAvailable
                        }

                        checkResults += mapOf(type to AddressCheckData(false, checkResult))
                        emitState()
                    }
                }
            }

            inputState = if (
                addressValidationError == null &&
                checkResults.none {
                    it.value.checkResult == AddressCheckResult.Detected ||
                            it.value.checkResult == AddressCheckResult.AlphaAmlVeryHigh
                }
            )
                DataState.Success(address)
            else
                DataState.Error(Exception())

            emitState()
        } catch (_: CancellationException) {
        } catch (e: Throwable) {
            inputState = DataState.Error(e)

            emitState()
        }
    }

    private fun parseDomain(addressText: String): Address {
        return domainParser.supportedHandler(addressText)?.parseAddress(addressText) ?: Address(
            addressText
        )
    }

    class Factory(
        private val token: Token,
        private val address: String,
        private val addressCheckerSkippable: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val blockchainType = token.blockchainType
            val coinCode = token.coin.code
            val tokenQuery = TokenQuery(blockchainType, token.type)
            val ensHandler = AddressHandlerEns(blockchainType, EnsResolverHolder.resolver)
            val udnHandler =
                AddressHandlerUdn(tokenQuery, coinCode, AppConfigProvider.udnApiKey)
            val addressParserChain =
                AddressParserChain(domainHandlers = listOf(ensHandler, udnHandler))
            val addressUriParser = AddressUriParser(token.blockchainType, token.type)
            val addressValidator = AddressValidatorFactory.get(token)
            val addressCheckManager = AddressCheckManager(
                getKoinInstance(),
                getKoinInstance(),
                getKoinInstance()
            )
            return SecurityCheckViewModel(
                token = token,
                addressUriParser = addressUriParser,
                initialAddress = address,
                localStorage = getKoinInstance(),
                addressCheckerSkippable = addressCheckerSkippable,
                domainParser = addressParserChain,
                addressValidator = addressValidator,
                addressCheckManager = addressCheckManager,
            ) as T
        }
    }
}

data class SecurityCheckUiState(
    val canBeSendToAddress: Boolean,
    val value: String,
    val inputState: DataState<Address>?,
    val address: Address?,
    val addressValidationInProgress: Boolean,
    val addressValidationError: Throwable?,
    val checkResults: Map<AddressCheckType, AddressCheckData>,
)

data class AddressCheckData(
    val inProgress: Boolean,
    val checkResult: AddressCheckResult = AddressCheckResult.NotAvailable
)
