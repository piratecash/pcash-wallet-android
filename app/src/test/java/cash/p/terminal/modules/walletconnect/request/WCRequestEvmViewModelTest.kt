package cash.p.terminal.modules.walletconnect.request

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitManager
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.core.managers.EvmMessageSigning
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationTargetException

// org.json.JSONArray needs a real implementation (not the JVM unit-test stub), hence Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WCRequestEvmViewModelTest {

    @After
    fun tearDown() {
        unmockkObject(WCDelegate)
        unmockkObject(EvmMessageSigning)
        WCDelegate.sessionRequestEvent = null
    }

    private fun buildSessionRequest(params: String) = Wallet.Model.SessionRequest(
        topic = "topic",
        chainId = "eip155:1",
        peerMetaData = null,
        request = Wallet.Model.SessionRequest.JSONRPCRequest(
            id = 1L,
            method = "personal_sign",
            params = params,
        ),
    )

    private fun viewModelWithParams(params: String): WCRequestEvmViewModel {
        WCDelegate.sessionRequestEvent = buildSessionRequest(params)

        val wcManager: WCManager = mockk(relaxed = true) {
            every { getBlockchainType(any()) } returns null
        }

        return WCRequestEvmViewModel(
            accountManager = mockk<IAccountManager>(relaxed = true),
            evmBlockchainManager = mockk<EvmBlockchainManager>(relaxed = true),
            wcManager = wcManager,
        )
    }

    /**
     * Builds a VM whose `allow()` can reach the signing step: a non-null resolved
     * [WCRequestEvmViewModel]'s evmKitWrapper/signer chain, wired via mocks. Requires
     * `mockkObject(WCDelegate)` to already be in effect (this stubs `sessionRequestEvent`).
     */
    private fun viewModelReadyToSign(signerMock: Signer): WCRequestEvmViewModel {
        every { WCDelegate.sessionRequestEvent } returns
            buildSessionRequest("""["0x68656c6c6f", "0xAddress"]""")

        val account: Account = mockk(relaxed = true)
        val accountManager: IAccountManager = mockk(relaxed = true)
        every { accountManager.activeAccount } returns account

        val evmKitWrapper: EvmKitWrapper = mockk(relaxed = true)
        every { evmKitWrapper.signer } returns signerMock

        val evmKitManager: EvmKitManager = mockk(relaxed = true)
        coEvery { evmKitManager.getEvmKitWrapper(any(), any()) } returns evmKitWrapper

        val evmBlockchainManager: EvmBlockchainManager = mockk(relaxed = true)
        every { evmBlockchainManager.getEvmKitManager(any()) } returns evmKitManager

        val wcManager: WCManager = mockk(relaxed = true)
        every { wcManager.getBlockchainType(any()) } returns BlockchainType.Ethereum

        return WCRequestEvmViewModel(
            accountManager = accountManager,
            evmBlockchainManager = evmBlockchainManager,
            wcManager = wcManager,
        )
    }

    private fun WCRequestEvmViewModel.personalSignBytes(): ByteArray {
        val method = WCRequestEvmViewModel::class.java.getDeclaredMethod("personalSignBytes")
        method.isAccessible = true
        return try {
            method.invoke(this) as ByteArray
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    @Test
    fun personalSignBytes_hexPayload_decodesToOriginalBytesWithoutUtf8RoundTrip() {
        // 0xff00 is valid hex but NOT valid UTF-8 text: a String(...)->toByteArray() round trip
        // (the old behavior) would corrupt it via UTF-8 replacement characters.
        val viewModel = viewModelWithParams("""["0xff00", "0xAddress"]""")

        val bytes = viewModel.personalSignBytes()

        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00), bytes)
    }

    @Test
    fun personalSignBytes_nonHexPayload_fallsBackToRawUtf8Bytes() {
        val viewModel = viewModelWithParams("""["Not a hex string!", "0xAddress"]""")

        val bytes = viewModel.personalSignBytes()

        assertArrayEquals("Not a hex string!".toByteArray(), bytes)
    }

    @Test
    fun personalSignBytes_emptyParamsArray_throwsIllegalArgumentException() {
        val viewModel = viewModelWithParams("[]")

        assertThrows(IllegalArgumentException::class.java) {
            viewModel.personalSignBytes()
        }
    }

    @Test
    fun allow_signingThrows_respondsWithErrorAndPropagatesException() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)

        val signingError = TrezorSigningException("Trezor operation cancelled by user")
        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } throws signingError
        every {
            WCDelegate.respondError(any(), any(), any(), captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        val thrown = assertThrows(TrezorSigningException::class.java) {
            runBlocking { viewModel.allow() }
        }

        assertEquals(signingError, thrown)
        verify { WCDelegate.respondError(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
    }

    @Test
    fun allow_signingSucceeds_respondsWithPendingRequestResult() {
        mockkObject(WCDelegate)
        mockkObject(EvmMessageSigning)

        coEvery { EvmMessageSigning.signPersonalMessage(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every {
            WCDelegate.respondPendingRequest(any(), any(), any(), captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke()
        }

        val viewModel = viewModelReadyToSign(mockk(relaxed = true))

        runBlocking { viewModel.allow() }

        verify { WCDelegate.respondPendingRequest(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { WCDelegate.respondError(any(), any(), any(), any(), any()) }
    }
}
