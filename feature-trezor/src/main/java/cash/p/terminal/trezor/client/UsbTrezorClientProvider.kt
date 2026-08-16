package cash.p.terminal.trezor.client

import cash.p.terminal.trezor.domain.TrezorCancelledException as DomainTrezorCancelledException
import cash.p.terminal.trezorkit.TrezorCancelledException as KitTrezorCancelledException
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.UsbTrezorClient
import cash.p.terminal.trezorkit.protocol.MessageTypeRegistry
import cash.p.terminal.trezorkit.protocol.TrezorSession
import cash.p.terminal.trezorkit.transport.TrezorUsbCoordinator
import io.horizontalsystems.core.DispatcherProvider

/**
 * [ITrezorClient] backed by the process-wide coordinator shared with native integrations.
 */
internal class UsbTrezorClientProvider(
    private val coordinator: TrezorUsbCoordinator,
    private val dispatcherProvider: DispatcherProvider,
) : ITrezorClient {

    override suspend fun <T> connect(block: suspend TrezorClientSession.() -> T): T =
        coordinator.withTransport { transport ->
            val session = TrezorSession(transport, MessageTypeRegistry, dispatcherProvider.io)
            try {
                UsbTrezorClient(session).connect(block)
            } catch (e: KitTrezorCancelledException) {
                // Map kit cancellation to the domain exception the send/swap UI already handles.
                throw DomainTrezorCancelledException()
            }
        }
}
