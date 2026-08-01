package cash.p.terminal.core.managers

import cash.p.terminal.core.providers.EvmLabelProvider
import cash.p.terminal.core.storage.EvmMethodLabelDao
import cash.p.terminal.core.storage.SyncerStateDao
import cash.p.terminal.entities.EvmMethodLabel
import cash.p.terminal.entities.SyncerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EvmLabelManagerTest {

    private companion object {
        const val ADDRESS_TIMESTAMP_KEY = "evm-label-manager-address-labels-timestamp"
        const val METHOD_TIMESTAMP_KEY = "evm-label-manager-method-labels-timestamp"
        const val BRIDGE_ADDRESS = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436"
    }

    private val provider = mockk<EvmLabelProvider>()
    private val addressLabelManager = mockk<AddressLabelManager>(relaxed = true)
    private val methodLabelDao = mockk<EvmMethodLabelDao>(relaxed = true)
    private val syncerStateStorage = mockk<SyncerStateDao>(relaxed = true)

    private lateinit var manager: EvmLabelManager

    @Before
    fun setUp() {
        manager = EvmLabelManager(
            provider = provider,
            addressLabelManager = addressLabelManager,
            methodLabelDao = methodLabelDao,
            syncerStateStorage = syncerStateStorage,
        )
    }

    @Test
    fun methodLabel_longInput_queriesFirstFourBytesInLowercase() {
        val input = byteArrayOf(
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte(),
            0xEE.toByte(),
        )
        every { methodLabelDao.get("0xaabbccdd") } returns EvmMethodLabel(
            methodId = "0xaabbccdd",
            label = "Transfer",
        )

        val result = manager.methodLabel(input)

        assertEquals("Transfer", result)
        verify(exactly = 1) { methodLabelDao.get("0xaabbccdd") }
    }

    @Test
    fun methodLabel_shortUnknownInput_queriesAvailableBytesAndReturnsNull() {
        every { methodLabelDao.get("0x010203") } returns null

        val result = manager.methodLabel(byteArrayOf(0x01, 0x02, 0x03))

        assertNull(result)
        verify(exactly = 1) { methodLabelDao.get("0x010203") }
    }

    @Test
    fun sync_changedTimestamps_persistsBothLabelSetsAndTimestamps() = runTest {
        every { syncerStateStorage.get(any()) } returns null
        coEvery { provider.updatesStatus() } returns EvmLabelProvider.UpdatesStatus(
            addressLabels = 11,
            evmMethodLabels = 22,
        )
        coEvery { provider.evmMethodLabels() } returns listOf(
            EvmLabelProvider.EvmMethodLabel("0xAABBCCDD", "Transfer"),
        )
        coEvery { provider.evmAddressLabels() } returns listOf(
            EvmLabelProvider.EvmAddressLabel(BRIDGE_ADDRESS, "Token Bridge"),
        )

        manager.sync()

        verify {
            methodLabelDao.update(listOf(EvmMethodLabel("0xaabbccdd", "Transfer")))
            syncerStateStorage.insert(SyncerState(METHOD_TIMESTAMP_KEY, "22"))
            syncerStateStorage.insert(SyncerState(ADDRESS_TIMESTAMP_KEY, "11"))
        }
        coVerify(exactly = 1) {
            addressLabelManager.replaceLegacy(
                listOf(LegacyAddressLabel(BRIDGE_ADDRESS, "Token Bridge"))
            )
        }
    }

    @Test
    fun sync_unchangedTimestamps_skipsDownloadsAndDatabaseUpdates() = runTest {
        every { syncerStateStorage.get(METHOD_TIMESTAMP_KEY) } returns SyncerState(
            METHOD_TIMESTAMP_KEY,
            "22",
        )
        every { syncerStateStorage.get(ADDRESS_TIMESTAMP_KEY) } returns SyncerState(
            ADDRESS_TIMESTAMP_KEY,
            "11",
        )
        coEvery { provider.updatesStatus() } returns EvmLabelProvider.UpdatesStatus(
            addressLabels = 11,
            evmMethodLabels = 22,
        )

        manager.sync()

        coVerify(exactly = 0) {
            provider.evmMethodLabels()
            provider.evmAddressLabels()
            addressLabelManager.replaceLegacy(any())
        }
        verify(exactly = 0) {
            methodLabelDao.update(any())
            syncerStateStorage.insert(any())
        }
    }

    @Test
    fun sync_onlyAddressTimestampChanged_updatesOnlyAddressLabels() = runTest {
        every { syncerStateStorage.get(METHOD_TIMESTAMP_KEY) } returns SyncerState(
            METHOD_TIMESTAMP_KEY,
            "22",
        )
        every { syncerStateStorage.get(ADDRESS_TIMESTAMP_KEY) } returns SyncerState(
            ADDRESS_TIMESTAMP_KEY,
            "10",
        )
        coEvery { provider.updatesStatus() } returns EvmLabelProvider.UpdatesStatus(
            addressLabels = 11,
            evmMethodLabels = 22,
        )
        coEvery { provider.evmAddressLabels() } returns listOf(
            EvmLabelProvider.EvmAddressLabel(BRIDGE_ADDRESS, "Token Bridge"),
        )

        manager.sync()

        coVerify(exactly = 0) { provider.evmMethodLabels() }
        verify(exactly = 0) { methodLabelDao.update(any()) }
        coVerify(exactly = 1) {
            addressLabelManager.replaceLegacy(
                listOf(LegacyAddressLabel(BRIDGE_ADDRESS, "Token Bridge"))
            )
        }
        verify(exactly = 1) {
            syncerStateStorage.insert(SyncerState(ADDRESS_TIMESTAMP_KEY, "11"))
        }
    }

    @Test
    fun sync_methodLabelDownloadFails_doesNotContinueWithAddressLabels() = runTest {
        every { syncerStateStorage.get(any()) } returns null
        coEvery { provider.updatesStatus() } returns EvmLabelProvider.UpdatesStatus(
            addressLabels = 11,
            evmMethodLabels = 22,
        )
        coEvery { provider.evmMethodLabels() } throws IllegalStateException("Network error")

        manager.sync()

        coVerify(exactly = 0) {
            provider.evmAddressLabels()
            addressLabelManager.replaceLegacy(any())
        }
        verify(exactly = 0) {
            methodLabelDao.update(any())
            syncerStateStorage.insert(any())
        }
    }

    @Test
    fun sync_addressPersistenceFails_doesNotStoreAddressTimestamp() = runTest {
        every { syncerStateStorage.get(METHOD_TIMESTAMP_KEY) } returns SyncerState(
            METHOD_TIMESTAMP_KEY,
            "22",
        )
        every { syncerStateStorage.get(ADDRESS_TIMESTAMP_KEY) } returns null
        coEvery { provider.updatesStatus() } returns EvmLabelProvider.UpdatesStatus(
            addressLabels = 11,
            evmMethodLabels = 22,
        )
        coEvery { provider.evmAddressLabels() } returns listOf(
            EvmLabelProvider.EvmAddressLabel(BRIDGE_ADDRESS, "Token Bridge"),
        )
        coEvery { addressLabelManager.replaceLegacy(any()) } throws
            IllegalStateException("Database error")

        manager.sync()

        verify(exactly = 0) {
            syncerStateStorage.insert(SyncerState(ADDRESS_TIMESTAMP_KEY, "11"))
        }
    }
}
