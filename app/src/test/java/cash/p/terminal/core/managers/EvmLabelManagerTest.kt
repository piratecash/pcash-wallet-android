package cash.p.terminal.core.managers

import cash.p.terminal.core.providers.EvmLabelProvider
import cash.p.terminal.core.storage.EvmAddressLabelDao
import cash.p.terminal.core.storage.EvmMethodLabelDao
import cash.p.terminal.core.storage.SyncerStateDao
import cash.p.terminal.entities.EvmAddressLabel
import cash.p.terminal.entities.EvmMethodLabel
import cash.p.terminal.entities.SyncerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EvmLabelManagerTest {

    private companion object {
        const val ADDRESS_TIMESTAMP_KEY = "evm-label-manager-address-labels-timestamp"
        const val METHOD_TIMESTAMP_KEY = "evm-label-manager-method-labels-timestamp"
        const val BRIDGE_ADDRESS = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436"
    }

    private val provider = mockk<EvmLabelProvider>()
    private val addressLabelDao = mockk<EvmAddressLabelDao>(relaxed = true)
    private val methodLabelDao = mockk<EvmMethodLabelDao>(relaxed = true)
    private val syncerStateStorage = mockk<SyncerStateDao>(relaxed = true)

    private lateinit var manager: EvmLabelManager

    @Before
    fun setUp() {
        manager = EvmLabelManager(
            provider = provider,
            addressLabelDao = addressLabelDao,
            methodLabelDao = methodLabelDao,
            syncerStateStorage = syncerStateStorage,
        )
    }

    @Test
    fun mapped_mixedCaseAddress_queriesLowercaseAndReturnsLabel() {
        every { addressLabelDao.get(BRIDGE_ADDRESS.lowercase()) } returns EvmAddressLabel(
            address = BRIDGE_ADDRESS.lowercase(),
            label = "Token Bridge",
        )

        val result = manager.mapped(BRIDGE_ADDRESS)

        assertEquals("Token Bridge", result)
        verify(exactly = 1) { addressLabelDao.get(BRIDGE_ADDRESS.lowercase()) }
    }

    @Test
    fun mapped_unknownAddress_returnsShortenedOriginalAddress() {
        val address = "0x123456789ABC"
        every { addressLabelDao.get(address.lowercase()) } returns null

        val result = manager.mapped(address)

        assertEquals("0x1234...9ABC", result)
        verify(exactly = 1) { addressLabelDao.get(address.lowercase()) }
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
    fun sync_changedTimestamps_normalizesAndPersistsBothLabelSets() {
        val completed = CountDownLatch(1)
        every { syncerStateStorage.get(any()) } returns null
        every { syncerStateStorage.insert(any()) } answers {
            if (firstArg<SyncerState>().key == ADDRESS_TIMESTAMP_KEY) {
                completed.countDown()
            }
        }
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

        syncAndAwait(completed)
        verify(exactly = 1) {
            methodLabelDao.update(listOf(EvmMethodLabel("0xaabbccdd", "Transfer")))
            addressLabelDao.update(
                listOf(EvmAddressLabel(BRIDGE_ADDRESS.lowercase(), "Token Bridge"))
            )
            syncerStateStorage.insert(SyncerState(METHOD_TIMESTAMP_KEY, "22"))
            syncerStateStorage.insert(SyncerState(ADDRESS_TIMESTAMP_KEY, "11"))
        }
    }

    @Test
    fun sync_unchangedTimestamps_skipsLabelDownloadsAndDatabaseUpdates() {
        val completed = CountDownLatch(1)
        every { syncerStateStorage.get(METHOD_TIMESTAMP_KEY) } returns SyncerState(
            METHOD_TIMESTAMP_KEY,
            "22",
        )
        every { syncerStateStorage.get(ADDRESS_TIMESTAMP_KEY) } answers {
            completed.countDown()
            SyncerState(ADDRESS_TIMESTAMP_KEY, "11")
        }
        coEvery { provider.updatesStatus() } returns EvmLabelProvider.UpdatesStatus(
            addressLabels = 11,
            evmMethodLabels = 22,
        )

        syncAndAwait(completed)
        coVerify(exactly = 0) {
            provider.evmMethodLabels()
            provider.evmAddressLabels()
        }
        verify(exactly = 0) {
            methodLabelDao.update(any())
            addressLabelDao.update(any())
            syncerStateStorage.insert(any())
        }
    }

    @Test
    fun sync_onlyAddressTimestampChanged_updatesOnlyAddressLabels() {
        val completed = CountDownLatch(1)
        every { syncerStateStorage.get(METHOD_TIMESTAMP_KEY) } returns SyncerState(
            METHOD_TIMESTAMP_KEY,
            "22",
        )
        every { syncerStateStorage.get(ADDRESS_TIMESTAMP_KEY) } returns SyncerState(
            ADDRESS_TIMESTAMP_KEY,
            "10",
        )
        every { syncerStateStorage.insert(any()) } answers {
            if (firstArg<SyncerState>().key == ADDRESS_TIMESTAMP_KEY) {
                completed.countDown()
            }
        }
        coEvery { provider.updatesStatus() } returns EvmLabelProvider.UpdatesStatus(
            addressLabels = 11,
            evmMethodLabels = 22,
        )
        coEvery { provider.evmAddressLabels() } returns listOf(
            EvmLabelProvider.EvmAddressLabel(BRIDGE_ADDRESS, "Token Bridge"),
        )

        syncAndAwait(completed)
        coVerify(exactly = 0) { provider.evmMethodLabels() }
        verify(exactly = 0) { methodLabelDao.update(any()) }
        coVerify(exactly = 1) { provider.evmAddressLabels() }
        verify(exactly = 1) {
            addressLabelDao.update(
                listOf(EvmAddressLabel(BRIDGE_ADDRESS.lowercase(), "Token Bridge"))
            )
            syncerStateStorage.insert(SyncerState(ADDRESS_TIMESTAMP_KEY, "11"))
        }
    }

    @Test
    fun sync_methodLabelDownloadFails_doesNotContinueWithAddressLabels() {
        val failed = CountDownLatch(1)
        every { syncerStateStorage.get(any()) } returns null
        coEvery { provider.updatesStatus() } returns EvmLabelProvider.UpdatesStatus(
            addressLabels = 11,
            evmMethodLabels = 22,
        )
        coEvery { provider.evmMethodLabels() } coAnswers {
            failed.countDown()
            error("Network error")
        }

        syncAndAwait(failed)
        coVerify(exactly = 0) { provider.evmAddressLabels() }
        verify(exactly = 0) {
            methodLabelDao.update(any())
            addressLabelDao.update(any())
            syncerStateStorage.insert(any())
        }
    }

    private fun syncAndAwait(completion: CountDownLatch) {
        manager.sync()
        assertTrue(completion.await(5, TimeUnit.SECONDS))
    }
}
