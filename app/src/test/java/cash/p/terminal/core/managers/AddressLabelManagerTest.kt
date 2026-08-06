package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.AddressLabelDao
import cash.p.terminal.entities.AddressLabel
import cash.p.terminal.entities.AddressLabelSource
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class AddressLabelManagerTest {

    private companion object {
        const val ADDRESS = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436"
        const val NORMALIZED_ADDRESS = "0x579fedb9253cca1b3114d5e2fa44f8158d61e436"
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val ioDispatcher = RecordingDispatcher()
    private val dao = mockk<AddressLabelDao>(relaxed = true) {
        coEvery { replaceAndGetAll(any(), any()) } returns emptyList()
    }
    private val manager = AddressLabelManager(
        addressLabelDao = dao,
        dispatcherProvider = TestDispatcherProvider(
            dispatcher = dispatcher,
            applicationScope = CoroutineScope(dispatcher),
            io = ioDispatcher,
        ),
    )

    @Test
    fun label_exactBlockchainLabelExists_returnsExactLabelWithoutFallback() = runTest(dispatcher) {
        load(
            label(BlockchainType.BinanceSmartChain.uid, AddressLabelSource.BUILT_IN, "BSC Bridge"),
            label("evm", AddressLabelSource.LEGACY_API, "Legacy Bridge"),
        )

        assertEquals(
            "BSC Bridge",
            manager.label(BlockchainType.BinanceSmartChain, ADDRESS),
        )
    }

    @Test
    fun label_exactBlockchainLabelMissing_returnsEvmFallback() = runTest(dispatcher) {
        load(label("evm", AddressLabelSource.LEGACY_API, "Legacy Bridge"))

        assertEquals(
            "Legacy Bridge",
            manager.label(BlockchainType.BinanceSmartChain, ADDRESS),
        )
    }

    @Test
    fun label_sameAddressOnDifferentBlockchains_returnsScopedLabels() = runTest(dispatcher) {
        load(
            label(BlockchainType.BinanceSmartChain.uid, AddressLabelSource.BUILT_IN, "BSC Bridge"),
            label(BlockchainType.Ethereum.uid, AddressLabelSource.BUILT_IN, "Ethereum Bridge"),
        )

        assertEquals(
            "BSC Bridge",
            manager.label(BlockchainType.BinanceSmartChain, ADDRESS),
        )
        assertEquals(
            "Ethereum Bridge",
            manager.label(BlockchainType.Ethereum, ADDRESS),
        )
    }

    @Test
    fun label_sameScopeAndAddress_returnsHighestPrioritySource() = runTest(dispatcher) {
        load(
            label("evm", AddressLabelSource.LEGACY_API, "Legacy"),
            label("evm", AddressLabelSource.BUILT_IN, "Built-in"),
            label("evm", AddressLabelSource.REMOTE, "Remote"),
        )

        assertEquals(
            "Built-in",
            manager.label(BlockchainType.Ethereum, ADDRESS),
        )
    }

    @Test
    fun label_evmMixedCaseAddress_usesNormalizedSnapshotKey() = runTest(dispatcher) {
        load(label(BlockchainType.Ethereum.uid, AddressLabelSource.BUILT_IN, "Bridge"))

        assertEquals("Bridge", manager.label(BlockchainType.Ethereum, ADDRESS))
    }

    @Test
    fun label_nonEvmAddress_preservesCaseAndDoesNotUseEvmFallback() = runTest(dispatcher) {
        val address = "CaseSensitiveAddress"
        load(
            label(
                scope = BlockchainType.Solana.uid,
                source = AddressLabelSource.BUILT_IN,
                value = "Solana Label",
                address = address,
            ),
            label(
                scope = "evm",
                source = AddressLabelSource.LEGACY_API,
                value = "EVM Label",
                address = address.lowercase(),
            ),
        )

        assertEquals("Solana Label", manager.label(BlockchainType.Solana, address))
        assertNull(manager.label(BlockchainType.Solana, address.lowercase()))
    }

    @Test
    fun mapped_unknownNonEvmAddress_returnsShortenedOriginalAddress() = runTest(dispatcher) {
        load()

        assertEquals(
            "Case...ress",
            manager.mapped(BlockchainType.Solana, "CaseSensitiveAddress"),
        )
    }

    @Test
    fun initialize_existingData_replacesOnlyBuiltInSourceWithNormalizedConfig() =
        runTest(dispatcher) {
            manager.initialize()

            coVerify(exactly = 1) {
                dao.replaceAndGetAll(
                    AddressLabelSource.BUILT_IN,
                    listOf(
                        AddressLabel(
                            scope = BlockchainType.BinanceSmartChain.uid,
                            normalizedAddress = NORMALIZED_ADDRESS,
                            source = AddressLabelSource.BUILT_IN,
                            label = "Token Bridge",
                        )
                    ),
                )
            }
        }

    @Test
    fun initialize_calledFromCallerDispatcher_runsDaoOnIoDispatcher() = runTest(dispatcher) {
        manager.initialize()

        assertTrue(ioDispatcher.dispatchCount > 0)
    }

    @Test
    fun initialize_successfulReplacement_emitsLabelsChanged() = runTest(dispatcher) {
        val labelsChanged = async(start = CoroutineStart.UNDISPATCHED) {
            manager.labelsChangedFlow.first()
        }

        manager.initialize()

        assertEquals(Unit, labelsChanged.await())
    }

    @Test
    fun replaceLegacy_mixedCaseAddresses_replacesOnlyLegacySourceAndRefreshesSnapshot() =
        runTest(dispatcher) {
            val expected = AddressLabel(
                scope = "evm",
                normalizedAddress = NORMALIZED_ADDRESS,
                source = AddressLabelSource.LEGACY_API,
                label = "Legacy Bridge",
            )
            coEvery {
                dao.replaceAndGetAll(AddressLabelSource.LEGACY_API, listOf(expected))
            } returns listOf(expected)

            manager.replaceLegacy(listOf(LegacyAddressLabel(ADDRESS, "Legacy Bridge")))

            assertEquals(
                "Legacy Bridge",
                manager.label(BlockchainType.Ethereum, ADDRESS),
            )
            coVerify(exactly = 1) {
                dao.replaceAndGetAll(AddressLabelSource.LEGACY_API, listOf(expected))
            }
        }

    @Test
    fun replaceLegacy_databaseFailure_keepsPreviousSnapshot() = runTest(dispatcher) {
        load(label("evm", AddressLabelSource.LEGACY_API, "Existing Label"))
        coEvery {
            dao.replaceAndGetAll(AddressLabelSource.LEGACY_API, any())
        } throws IllegalStateException("Database error")

        assertFailsWith<IllegalStateException> {
            manager.replaceLegacy(listOf(LegacyAddressLabel(ADDRESS, "Updated Label")))
        }

        assertEquals(
            "Existing Label",
            manager.label(BlockchainType.Ethereum, ADDRESS),
        )
    }

    private suspend fun load(vararg labels: AddressLabel) {
        coEvery { dao.replaceAndGetAll(any(), any()) } returns labels.toList()
        manager.initialize()
    }

    private fun label(
        scope: String,
        source: AddressLabelSource,
        value: String,
        address: String = NORMALIZED_ADDRESS,
    ) = AddressLabel(
        scope = scope,
        normalizedAddress = address,
        source = source,
        label = value,
    )

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            block.run()
        }
    }
}
