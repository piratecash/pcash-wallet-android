package cash.p.terminal.core.managers

import cash.p.terminal.core.providers.EvmLabelProvider
import cash.p.terminal.core.storage.EvmMethodLabelDao
import cash.p.terminal.core.storage.SyncerStateDao
import cash.p.terminal.core.to0xHexString
import cash.p.terminal.entities.EvmMethodLabel
import cash.p.terminal.entities.SyncerState
import timber.log.Timber

class EvmLabelManager(
    private val provider: EvmLabelProvider,
    private val addressLabelManager: AddressLabelManager,
    private val methodLabelDao: EvmMethodLabelDao,
    private val syncerStateStorage: SyncerStateDao
) {
    private val keyMethodLabelsTimestamp = "evm-label-manager-method-labels-timestamp"
    private val keyAddressLabelsTimestamp = "evm-label-manager-address-labels-timestamp"

    suspend fun sync() {
        try {
            val updatesStatus = provider.updatesStatus()
            syncMethodLabels(updatesStatus.evmMethodLabels)
            syncAddressLabels(updatesStatus.addressLabels)
        } catch (e: Exception) {
            Timber.e(e, "EVM label sync failed")
        }
    }

    fun methodLabel(input: ByteArray): String? {
        val methodId = input.take(4).toByteArray().to0xHexString()
        return methodLabelDao.get(methodId.lowercase())?.label
    }

    private suspend fun syncAddressLabels(timestamp: Long) {
        val lastSyncTimestamp = syncerStateStorage.get(keyAddressLabelsTimestamp)?.value?.toLongOrNull()
        if (lastSyncTimestamp == timestamp) return

        val addressLabels = provider.evmAddressLabels()
        addressLabelManager.replaceLegacy(
            addressLabels.map { LegacyAddressLabel(it.address, it.label) }
        )

        syncerStateStorage.insert(SyncerState(keyAddressLabelsTimestamp, timestamp.toString()))
    }

    private suspend fun syncMethodLabels(timestamp: Long) {
        val lastSyncTimestamp = syncerStateStorage.get(keyMethodLabelsTimestamp)?.value?.toLongOrNull()
        if (lastSyncTimestamp == timestamp) return

        val methodLabels = provider.evmMethodLabels()
        methodLabelDao.update(methodLabels.map { EvmMethodLabel(it.methodId.lowercase(), it.label) })

        syncerStateStorage.insert(SyncerState(keyMethodLabelsTimestamp, timestamp.toString()))
    }
}
