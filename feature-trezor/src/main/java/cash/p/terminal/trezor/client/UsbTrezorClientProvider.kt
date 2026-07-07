package cash.p.terminal.trezor.client

import cash.p.terminal.trezor.domain.TrezorCancelledException as DomainTrezorCancelledException
import cash.p.terminal.trezorkit.TrezorCancelledException as KitTrezorCancelledException
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.UsbTrezorClient
import cash.p.terminal.trezorkit.protocol.MessageTypeRegistry
import cash.p.terminal.trezorkit.protocol.TrezorSession
import cash.p.terminal.trezorkit.transport.UsbTrezorTransport
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ITrezorClient] backed by the direct-USB kit. Every read caller reaches USB through here, so
 * device acquisition (incl. the permission prompt) and the session both live inside [connect]:
 * any consumer - create-wallet or scan-to-add - transparently gets the device, and a single
 * [Mutex] keeps USB operations globally one-at-a-time across the app.
 */
internal class UsbTrezorClientProvider(
    private val connection: TrezorUsbConnection,
    private val dispatcherProvider: DispatcherProvider,
) : ITrezorClient {

    private val mutex = Mutex()

    override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T =
        mutex.withLock {
            val device = connection.acquire()
            val transport = UsbTrezorTransport(connection.usbManager, device)
            val session = TrezorSession(transport, MessageTypeRegistry, dispatcherProvider.io)
            try {
                UsbTrezorClient(session).connect(block)
            } catch (e: KitTrezorCancelledException) {
                // Map kit cancellation to the domain exception the send/swap UI already handles.
                throw DomainTrezorCancelledException()
            }
        }
}
