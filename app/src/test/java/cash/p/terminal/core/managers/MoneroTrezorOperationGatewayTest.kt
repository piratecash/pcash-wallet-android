package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorSessionId
import cash.p.terminal.trezorkit.TrezorUsbDisconnectedException
import cash.p.terminal.trezorkit.TrezorUsbOperationTimeoutException
import cash.p.terminal.trezorkit.TrezorUsbShortPacketException
import cash.p.terminal.trezorkit.protocol.FramingCodec
import cash.p.terminal.trezorkit.transport.TrezorRawUsbChannel
import cash.p.terminal.trezorkit.transport.TrezorUsbCoordinator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import com.google.protobuf.ByteString
import com.piratecash.monero.signer.ExternalSignerException
import com.piratecash.monero.signer.ExternalSignerRegistration
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage.MessageType
import com.satoshilabs.trezor.lib.protobuf.TrezorMessageBitcoin.PublicKey
import com.satoshilabs.trezor.lib.protobuf.TrezorMessageCommon.HDNodeType
import com.satoshilabs.trezor.lib.protobuf.TrezorMessageManagement.Features
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroTrezorOperationGatewayTest {

    @Test
    fun execute_callerCancelled_rethrowsStructuredCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        val coordinator = mockk<TrezorUsbCoordinator> {
            coEvery { withRawChannel<String>(any()) } throws cancellation
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator = coordinator,
            readiness = mockk(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )

        val actual = assertFailsWith<CancellationException> {
            gateway.execute { "unused" }
        }

        assertSame(cancellation, actual)
    }

    @Test
    fun execute_externalSignerFailure_returnsUnifiedHardwareFailure() = runTest {
        val coordinator = mockk<TrezorUsbCoordinator> {
            coEvery { withRawChannel<String>(any()) } throws ExternalSignerException(
                HardwareWalletErrorCode.Disconnected,
                "disconnected",
            )
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator = coordinator,
            readiness = mockk(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.execute { "unused" }
        }

        assertEquals(HardwareWalletErrorCode.Disconnected, error.error)
        assertTrue(error.cause is ExternalSignerException)
    }

    @Test
    fun execute_externalSignerCancellation_preservesCancellationCode() = runTest {
        val coordinator = mockk<TrezorUsbCoordinator> {
            coEvery { withRawChannel<String>(any()) } throws ExternalSignerException(
                HardwareWalletErrorCode.Cancelled,
                "cancelled",
            )
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator = coordinator,
            readiness = mockk(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.execute { "unused" }
        }

        assertEquals(HardwareWalletErrorCode.Cancelled, error.error)
    }

    @Test
    fun execute_nativeOwnershipRetained_preservesCleanupSignal() = runTest {
        val ownershipRetained = MoneroDeviceWalletOwnershipRetainedException(
            HardwareWalletOperationException(
                HardwareWalletErrorCode.Protocol,
                "close failed",
            ),
        )
        val coordinator = mockk<TrezorUsbCoordinator> {
            coEvery { withRawChannel<String>(any()) } throws ownershipRetained
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator = coordinator,
            readiness = mockk(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )

        val actual = assertFailsWith<MoneroDeviceWalletOwnershipRetainedException> {
            gateway.execute { "unused" }
        }

        assertSame(ownershipRetained, actual)
    }

    @Test
    fun execute_legacyAccount_returnsLiveWalletIdentityForBinding() = runTest {
        val channel = scriptedChannel(LIVE_WALLET_KEY)
        val coordinator = coordinator(channel)
        val sessionId = TrezorSessionId(SESSION_ID)
        val readiness = readiness(sessionId)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator,
            readiness,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )

        val result = gateway.execute(legacyAccount()) { it }

        assertEquals(LIVE_WALLET_KEY, result)
        assertFailsWith<IllegalStateException> { sessionId.toByteArray() }
        verify(exactly = 0) { readiness.requireWallet(any(), any()) }
    }

    @Test
    fun execute_boundAccountWrongWallet_rejectsBinding() = runTest {
        val channel = scriptedChannel(LIVE_WALLET_KEY)
        val coordinator = coordinator(channel)
        val sessionId = TrezorSessionId(SESSION_ID)
        val readiness = readiness(sessionId)
        every {
            readiness.requireWallet("different-wallet", LIVE_WALLET_KEY)
        } throws HardwareWalletOperationException(
            HardwareWalletErrorCode.WrongWallet,
            "wrong wallet",
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator,
            readiness,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.execute(
                legacyAccount().copy(
                    type = (legacyAccount().type as AccountType.TrezorDevice).copy(
                        walletPublicKey = "different-wallet",
                    ),
                ),
            ) { "unused" }
        }

        assertEquals(HardwareWalletErrorCode.WrongWallet, error.error)
        assertFailsWith<IllegalStateException> { sessionId.toByteArray() }
    }

    @Test
    fun withExternalSignerRegistration_success_releasesRegistration() = runTest {
        val registration = TestRegistration()

        withExternalSignerRegistration(registration) {}

        assertEquals(1, registration.releaseCount)
    }

    @Test
    fun withExternalSignerRegistration_operationFails_releasesAndRethrowsOperationFailure() = runTest {
        val operationFailure = IllegalStateException("operation")
        val registration = TestRegistration()

        val actual = captureFailure {
            withExternalSignerRegistration(registration) {
                throw operationFailure
            }
        }

        assertSame(operationFailure, actual)
        assertEquals(1, registration.releaseCount)
    }

    @Test
    fun withExternalSignerRegistration_releaseFails_rethrowsReleaseFailure() = runTest {
        val releaseFailure = IllegalStateException("release")
        val registration = TestRegistration(releaseFailure)

        val actual = captureFailure {
            withExternalSignerRegistration(registration) {}
        }

        assertSame(releaseFailure, actual)
    }

    @Test
    fun withExternalSignerRegistration_operationAndReleaseFail_suppressesReleaseFailure() = runTest {
        val operationFailure = IllegalStateException("operation")
        val releaseFailure = IllegalStateException("release")
        val registration = TestRegistration(releaseFailure)

        val actual = captureFailure {
            withExternalSignerRegistration(registration) {
                throw operationFailure
            }
        }

        assertSame(operationFailure, actual)
        assertEquals(listOf(releaseFailure), actual.suppressed.toList())
    }

    @Test
    fun withExternalSignerRegistration_callerCancelled_signalsBeforeRelease() = runTest {
        val registration = TestRegistration()
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            withExternalSignerRegistration(registration) {
                awaitCancellation()
            }
        }

        operation.cancelAndJoin()

        assertEquals(1, registration.signalCancellationCount)
        assertEquals(1, registration.releaseCount)
    }

    @Test
    fun execute_preflightReadBlocked_cancellationInterruptsRawChannel() = runTest {
        val readStarted = CountDownLatch(1)
        val readInterrupted = CountDownLatch(1)
        val channel = object : TrezorRawUsbChannel {
            override fun writePacket(packet: ByteArray) = Unit

            override fun readPacket(): ByteArray {
                readStarted.countDown()
                try {
                    CountDownLatch(1).await()
                } catch (error: InterruptedException) {
                    readInterrupted.countDown()
                    throw error
                }
                error("Unreachable")
            }

            override fun readPacket(deadlineNanos: Long): ByteArray = readPacket()

            override fun cancel() = Unit
        }
        val gateway = MoneroTrezorOperationGateway(
            coordinator(channel),
            mockk(),
            TestDispatcherProvider(Dispatchers.IO, CoroutineScope(Dispatchers.IO)),
        )
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.execute { "unused" }
        }
        assertTrue(readStarted.await(1, TimeUnit.SECONDS))

        operation.cancelAndJoin()

        assertTrue(readInterrupted.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun externalSignerChannel_packetTimeout_preservesHardwareError() {
        assertChannelFailure(
            TrezorUsbOperationTimeoutException(),
            HardwareWalletErrorCode.PacketTimeout,
        )
    }

    @Test
    fun externalSignerChannel_disconnected_preservesHardwareError() {
        assertChannelFailure(
            TrezorUsbDisconnectedException(),
            HardwareWalletErrorCode.Disconnected,
        )
    }

    @Test
    fun externalSignerChannel_shortPacket_preservesHardwareError() {
        assertChannelFailure(
            TrezorUsbShortPacketException("read", 32),
            HardwareWalletErrorCode.ShortPacket,
        )
    }

    private fun assertChannelFailure(
        cause: Exception,
        expected: HardwareWalletErrorCode,
    ) {
        val rawChannel = mockk<TrezorRawUsbChannel> {
            every { writePacket(any()) } throws cause
        }

        val error = assertFailsWith<ExternalSignerException> {
            TrezorExternalSignerChannel(rawChannel).writePacket(ByteArray(64))
        }

        assertEquals(expected, error.hardwareErrorCode)
        assertSame(cause, error.cause)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable =
        try {
            block()
            fail("Expected failure")
            throw AssertionError("unreachable")
        } catch (error: Throwable) {
            error
        }

    private fun coordinator(
        channel: TrezorRawUsbChannel,
    ) = mockk<TrezorUsbCoordinator> {
        coEvery { withRawChannel<String>(any()) } coAnswers {
            firstArg<suspend (TrezorRawUsbChannel) -> String>().invoke(channel)
        }
    }

    private fun readiness(
        sessionId: TrezorSessionId = TrezorSessionId(SESSION_ID),
    ) = mockk<MoneroTrezorReadiness> {
        every { requireLive(any<TrezorFeatures>(), any()) } answers { firstArg() }
        every { requireSession(any()) } returns sessionId
        every { requireWallet(any(), any()) } just Runs
    }

    private fun scriptedChannel(walletKey: String): TrezorRawUsbChannel {
        val features = Features.newBuilder()
            .setDeviceId("device-id")
            .setInternalModel("T3T1")
            .setMajorVersion(2)
            .setMinorVersion(8)
            .setPatchVersion(10)
            .setSessionId(ByteString.copyFrom(SESSION_ID))
            .build()
        val publicKey = PublicKey.newBuilder()
            .setXpub(walletKey)
            .setNode(
                HDNodeType.newBuilder()
                    .setDepth(0)
                    .setFingerprint(0)
                    .setChildNum(0)
                    .setPublicKey(ByteString.copyFrom(byteArrayOf(2, 3)))
                    .setChainCode(ByteString.copyFrom(byteArrayOf(4, 5)))
                    .build(),
            )
            .build()
        val responses =
            FramingCodec.encode(MessageType.MessageType_Features_VALUE, features.toByteArray()) +
                FramingCodec.encode(MessageType.MessageType_PublicKey_VALUE, publicKey.toByteArray())
        return ScriptedRawChannel(responses)
    }

    private fun legacyAccount() = Account(
        id = ACCOUNT_ID,
        name = "Legacy Safe 5",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "",
        ),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = false,
    )

    private class TestRegistration(
        private val releaseFailure: Throwable? = null,
    ) : ExternalSignerRegistration {
        var releaseCount = 0
            private set
        var signalCancellationCount = 0
            private set

        override fun signalCancellation() {
            signalCancellationCount++
        }

        override fun cancel() = Unit

        override fun release() {
            releaseCount++
            releaseFailure?.let { throw it }
        }
    }

    private class ScriptedRawChannel(
        responses: List<ByteArray>,
    ) : TrezorRawUsbChannel {
        private val responses = ArrayDeque(responses)

        override fun writePacket(packet: ByteArray) = Unit

        override fun readPacket(): ByteArray = responses.removeFirst()

        override fun readPacket(deadlineNanos: Long): ByteArray = readPacket()

        override fun cancel() = Unit
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val LIVE_WALLET_KEY = "zpub-live-wallet"
        val SESSION_ID = byteArrayOf(1, 2, 3, 4)
    }
}
