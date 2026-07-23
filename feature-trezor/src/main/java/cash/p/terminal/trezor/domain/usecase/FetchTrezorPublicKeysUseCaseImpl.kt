package cash.p.terminal.trezor.domain.usecase

import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery

internal class FetchTrezorPublicKeysUseCaseImpl(
    private val trezorClient: ITrezorClient
) : FetchTrezorPublicKeysUseCase {

    override suspend fun invoke(
        tokenQueries: List<TokenQuery>,
        accountId: String
    ): List<HardwarePublicKey> = trezorClient.connect {
        // Firmware capability is only known from a live device, so gate Tron here rather than at the caller.
        val derivable = TrezorModelSupport.filterByFirmwareCapabilities(tokenQueries, getFeatures())
        val specs = TrezorPublicKeySpecs.buildQuerySpecs(derivable)
        if (specs.isEmpty()) return@connect emptyList()

        // Distinct requests collapse coins that share a derivation (e.g. all EVM chains) into one
        // on-device read; every query still gets its own HardwarePublicKey below.
        val uniqueRequests = specs.map { it.request }.distinct()
        val resultByRequest = uniqueRequests.zip(getPublicKeys(uniqueRequests)).toMap()

        specs.mapNotNull { spec ->
            resultByRequest[spec.request]?.let { TrezorPublicKeySpecs.toHardwarePublicKey(spec, it, accountId) }
        }
    }
}
