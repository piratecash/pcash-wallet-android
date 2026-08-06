package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.AddressLabelDao
import cash.p.terminal.entities.AddressLabel
import cash.p.terminal.entities.AddressLabelSource
import cash.p.terminal.strings.helpers.shorten
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class AddressLabelDefinition(
    val blockchainType: BlockchainType,
    val address: String,
    val label: String,
)

internal data class LegacyAddressLabel(
    val address: String,
    val label: String,
)

class AddressLabelManager(
    private val addressLabelDao: AddressLabelDao,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val _labelsChangedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val labelsChangedFlow: Flow<Unit> = _labelsChangedFlow.asSharedFlow()
    private val replaceMutex = Mutex()

    @Volatile
    private var labelsByAddress = emptyMap<AddressKey, String>()

    suspend fun initialize() {
        replace(
            source = AddressLabelSource.BUILT_IN,
            labels = BuiltInAddressLabels.all.map { definition ->
                definition.toEntity(AddressLabelSource.BUILT_IN)
            },
        )
    }

    internal suspend fun replaceLegacy(labels: List<LegacyAddressLabel>) {
        replace(
            source = AddressLabelSource.LEGACY_API,
            labels = labels.map { label ->
                createLabel(
                    scope = EVM_SCOPE,
                    address = label.address,
                    source = AddressLabelSource.LEGACY_API,
                    label = label.label,
                    isEvm = true,
                )
            },
        )
    }

    fun label(blockchainType: BlockchainType, address: String): String? {
        val isEvm = blockchainType in EvmBlockchainManager.blockchainTypes
        val normalizedAddress = normalize(address, isEvm)
        val labels = labelsByAddress
        return labels[AddressKey(blockchainType.uid, normalizedAddress)]
            ?: if (isEvm) labels[AddressKey(EVM_SCOPE, normalizedAddress)] else null
    }

    fun mapped(blockchainType: BlockchainType, address: String): String {
        return label(blockchainType, address) ?: address.shorten()
    }

    private fun AddressLabelDefinition.toEntity(source: AddressLabelSource): AddressLabel {
        return createLabel(
            scope = blockchainType.uid,
            address = address,
            source = source,
            label = label,
            isEvm = blockchainType in EvmBlockchainManager.blockchainTypes,
        )
    }

    private fun createLabel(
        scope: String,
        address: String,
        source: AddressLabelSource,
        label: String,
        isEvm: Boolean,
    ): AddressLabel {
        return AddressLabel(
            scope = scope,
            normalizedAddress = normalize(address, isEvm),
            source = source,
            label = label,
        )
    }

    private fun normalize(address: String, isEvm: Boolean): String {
        return if (isEvm) address.lowercase() else address
    }

    private suspend fun replace(
        source: AddressLabelSource,
        labels: List<AddressLabel>,
    ) = replaceMutex.withLock {
        withContext(dispatcherProvider.io) {
            labelsByAddress = addressLabelDao.replaceAndGetAll(source, labels).toLookup()
        }
        _labelsChangedFlow.tryEmit(Unit)
    }

    private fun List<AddressLabel>.toLookup(): Map<AddressKey, String> {
        return groupBy { AddressKey(it.scope, it.normalizedAddress) }
            .mapValues { (_, labels) ->
                labels.minBy { it.source.priority }.label
            }
    }

    private data class AddressKey(
        val scope: String,
        val normalizedAddress: String,
    )

    private companion object {
        const val EVM_SCOPE = "evm"
    }
}
