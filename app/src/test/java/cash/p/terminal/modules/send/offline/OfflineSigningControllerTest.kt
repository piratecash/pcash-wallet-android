package cash.p.terminal.modules.send.offline

import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.wallet.Wallet
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSigningControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val repository = mockk<OfflineSignedTransactionRepository>(relaxed = true)

    @Test
    fun sign_pcashPayloadTooLargeRawFits_signsWithRawTransferFormat() = runTest(dispatcher) {
        val controller = controller(this)
        every { payloadEncoder.encode(any()) } returns "a".repeat(TOO_LARGE_QR_PAYLOAD_SIZE)
        coEvery { repository.save(any(), any()) } returns Unit

        controller.sign(
            format = OfflineTransactionFormat.Pcash,
            producer = { Unit },
            draftBuilder = { draft() },
        )
        advanceUntilIdle()

        assertEquals(OfflineSignState.Signed(OfflineTransactionFormat.Raw), controller.signState)
    }

    @Test
    fun sign_pcashPayloadTooLargeAndRawTooLarge_keepsPcashTransferFormat() = runTest(dispatcher) {
        val controller = controller(this)
        every { payloadEncoder.encode(any()) } returns "a".repeat(TOO_LARGE_QR_PAYLOAD_SIZE)
        coEvery { repository.save(any(), any()) } returns Unit

        controller.sign(
            format = OfflineTransactionFormat.Pcash,
            producer = { Unit },
            draftBuilder = { draft(rawHex = "ab".repeat(TOO_LARGE_RAW_BYTES)) },
        )
        advanceUntilIdle()

        assertEquals(OfflineSignState.Signed(OfflineTransactionFormat.Pcash), controller.signState)
    }

    private fun controller(scope: CoroutineScope) = OfflineSigningController<Unit>(
        scope = scope,
        dispatcherProvider = TestDispatcherProvider(dispatcher, scope),
        payloadEncoder = payloadEncoder,
        repository = repository,
        cautionFactory = { mockk<HSCaution>() },
        isSilentCancellation = { false },
    )

    private fun draft(
        rawHex: String = "deadbeefdeadbeef",
    ) = OfflineSignedTransactionDraft(
        wallet = mockk<Wallet>(relaxed = true),
        amount = BigDecimal.ONE,
        fee = BigDecimal.ZERO,
        toAddress = "address",
        rawHex = rawHex,
        txHash = "tx-hash",
        inputOutpoints = emptyList(),
        createdAt = 1_700_000_000_000L,
    )

    private companion object {
        const val TOO_LARGE_RAW_BYTES = 2_000
        const val TOO_LARGE_QR_PAYLOAD_SIZE = 1_700
    }
}
