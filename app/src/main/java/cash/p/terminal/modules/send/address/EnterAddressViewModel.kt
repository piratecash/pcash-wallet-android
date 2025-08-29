package cash.p.terminal.modules.send.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.address.AddressCheckManager
import cash.p.terminal.core.address.AddressCheckType
import cash.p.terminal.core.factories.AddressValidatorFactory
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressHandlerEns
import cash.p.terminal.modules.address.AddressHandlerUdn
import cash.p.terminal.modules.address.AddressParserChain
import cash.p.terminal.modules.address.EnsResolverHolder
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class EnterAddressViewModel(
    private val token: Token,
    addressUriParser: AddressUriParser,
    initialAddress: String?,
    contactsRepository: ContactsRepository,
    private val localStorage: ILocalStorage,
    private val addressCheckerSkippable: Boolean,
    private val domainParser: AddressParserChain,
    private val addressValidator: EnterAddressValidator,
    private val addressCheckManager: AddressCheckManager,
) : ViewModelUiState<EnterAddressUiState>() {
    private val recentAddressManager: RecentAddressManager by inject(RecentAddressManager::class.java)
    private val checkPremiumUseCase: CheckPremiumUseCase by inject(CheckPremiumUseCase::class.java)

    private var address: Address? = null
    private val canBeSendToAddress: Boolean
        get() = address != null && !addressValidationInProgress && addressValidationError == null
    private var recentAddress: String? = recentAddressManager.getRecentAddress(token.blockchainType)
    private val contactNameAddresses =
        contactsRepository.getContactAddressesByBlockchain(token.blockchainType)
    private var addressValidationInProgress: Boolean = false
    private var addressValidationError: Throwable? = null

    private var checkResults: Map<AddressCheckType, AddressCheckData> = mapOf()
    private var value = ""
    private var inputState: DataState<Address>? = null
    private var parseAddressJob: Job? = null

    private val addressExtractor = AddressExtractor(token.blockchainType, addressUriParser)
    private val addressCheckByBaseEnabled: Boolean
        get() = if (addressCheckerSkippable) {
            localStorage.recipientAddressBaseCheckEnabled
        } else {
            true
        }
    private val addressCheckByContractEnabled: Boolean
        get() = if (addressCheckerSkippable) {
            localStorage.recipientAddressContractCheckEnabled && checkPremiumUseCase.getPremiumType().isPremium()
        } else {
            true
        }

    // To speed up address parsing, we cache parsed addresses
    private val parsedAddressCache = mutableMapOf<String, Address>()

    init {
        initialAddress?.let {
            onEnterAddress(initialAddress)
        }
    }

    override fun createState() = EnterAddressUiState(
        canBeSendToAddress = canBeSendToAddress,
        recentAddress = recentAddress,
        recentContact = recentAddress?.let { recent ->
            contactNameAddresses.find { it.contactAddress.address == recentAddress }
                ?.let { SContact(it.name, recent) }
        },
        contacts = contactNameAddresses.map { SContact(it.name, it.contactAddress.address) },
        value = value,
        inputState = inputState,
        address = address,
        addressValidationInProgress = addressValidationInProgress,
        addressValidationError = addressValidationError,
        checkResults = checkResults,
        addressCheckByBaseEnabled = addressCheckByBaseEnabled,
        addressCheckSmartContractEnabled = addressCheckByContractEnabled
    )

    fun onCheckBaseAddressClick(enabled: Boolean) {
        localStorage.recipientAddressBaseCheckEnabled = enabled
        emitState()

        if (value.isNotBlank()) {
            processAddress(value)
        }
    }

    fun onCheckSmartContractAddressClick(enabled: Boolean) {
        localStorage.recipientAddressContractCheckEnabled = enabled
        emitState()

        if (value.isNotBlank()) {
            processAddress(value)
        }
    }

    fun onEnterAddress(value: String) {
        parseAddressJob?.cancel()

        address = null
        inputState = null
        addressValidationInProgress = true
        addressValidationError = null
        checkResults = mapOf()

        if (value.isBlank()) {
            this.value = ""
            emitState()
        } else {
            try {
                val addressString = addressExtractor.extractAddressFromUri(value.trim())
                this.value = addressString
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
                    (it.isPremiumRequired() && !checkPremiumUseCase.getPremiumType().isPremium())// To promote in list
        }
    }

    private fun processAddress(addressText: String) {
        parseAddressJob = viewModelScope.launch {
            try {
                val address = parsedAddressCache.getOrPut(addressText) { parseDomain(addressText) }
                try {
                    withContext(Dispatchers.IO) {
                        addressValidator.validate(address)
                        ensureActive()
                    }

                    this@EnterAddressViewModel.address = address
                    addressValidationInProgress = false
                    addressValidationError = null

                    emitState()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Throwable) {
                    ensureActive()

                    this@EnterAddressViewModel.address = null
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
                                if (type.isPremiumRequired() && !checkPremiumUseCase.getPremiumType().isPremium()) {
                                    AddressCheckResult.NotAllowed
                                } else if (addressCheckManager.isClear(type, address, token)) {
                                    AddressCheckResult.Clear
                                } else {
                                    AddressCheckResult.Detected
                                }
                            } catch (e: Throwable) {
                                AddressCheckResult.NotAvailable
                            }

                            checkResults += mapOf(type to AddressCheckData(false, checkResult))
                            ensureActive()
                            emitState()
                        }
                    }
                }

                inputState = if (
                    addressValidationError == null &&
                    checkResults.none { it.value.checkResult == AddressCheckResult.Detected }
                )
                    DataState.Success(address)
                else
                    DataState.Error(Exception())

                emitState()
            } catch (_: CancellationException) {
            } catch (e: Throwable) {
                inputState = DataState.Error(e)

                ensureActive()
                emitState()
            }
        }
    }

    private fun parseDomain(addressText: String): Address {
        return domainParser.supportedHandler(addressText)?.parseAddress(addressText) ?: Address(
            addressText
        )
    }

    class Factory(
        private val token: Token,
        private val address: String?,
        private val addressCheckerSkippable: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val blockchainType = token.blockchainType
            val coinCode = token.coin.code
            val tokenQuery = TokenQuery(blockchainType, token.type)
            val ensHandler = AddressHandlerEns(blockchainType, EnsResolverHolder.resolver)
            val udnHandler =
                AddressHandlerUdn(tokenQuery, coinCode, App.appConfigProvider.udnApiKey)
            val addressParserChain =
                AddressParserChain(domainHandlers = listOf(ensHandler, udnHandler))
            val addressUriParser = AddressUriParser(token.blockchainType, token.type)
            val addressValidator = AddressValidatorFactory.get(token)
            val addressCheckManager = AddressCheckManager(
                App.spamManager,
                App.appConfigProvider,
                App.evmBlockchainManager,
                App.evmSyncSourceManager
            )
            return EnterAddressViewModel(
                token = token,
                addressUriParser = addressUriParser,
                initialAddress = address,
                contactsRepository = App.contactsRepository,
                localStorage = App.localStorage,
                addressCheckerSkippable = addressCheckerSkippable,
                domainParser = addressParserChain,
                addressValidator = addressValidator,
                addressCheckManager = addressCheckManager,
            ) as T
        }
    }
}

data class EnterAddressUiState(
    val canBeSendToAddress: Boolean,
    val recentAddress: String?,
    val recentContact: SContact?,
    val contacts: List<SContact>,
    val value: String,
    val inputState: DataState<Address>?,
    val address: Address?,
    val addressValidationInProgress: Boolean,
    val addressValidationError: Throwable?,
    val checkResults: Map<AddressCheckType, AddressCheckData>,
    val addressCheckByBaseEnabled: Boolean,
    val addressCheckSmartContractEnabled: Boolean,
)

data class AddressCheckData(
    val inProgress: Boolean,
    val checkResult: AddressCheckResult = AddressCheckResult.NotAvailable
)