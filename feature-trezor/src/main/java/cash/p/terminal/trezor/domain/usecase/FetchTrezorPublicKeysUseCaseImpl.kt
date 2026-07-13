package cash.p.terminal.trezor.domain.usecase

import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery

internal class FetchTrezorPublicKeysUseCaseImpl(
    private val trezorClient: ITrezorClient
) : FetchTrezorPublicKeysUseCase {

    override suspend fun invoke(
        tokenQueries: List<TokenQuery>,
        accountId: String
    ): List<HardwarePublicKey> {
        val specs = TrezorPublicKeySpecs.buildQuerySpecs(tokenQueries)
        if (specs.isEmpty()) return emptyList()

        // Distinct requests collapse coins that share a derivation (e.g. all EVM chains) into one
        // on-device read; every query still gets its own HardwarePublicKey below.
        val uniqueRequests = specs.map { it.request }.distinct()
        val resultByRequest = trezorClient
            .connect { getPublicKeys(uniqueRequests) }
            .let { results -> uniqueRequests.zip(results).toMap() }

        return specs.mapNotNull { spec ->
            resultByRequest[spec.request]?.let { TrezorPublicKeySpecs.toHardwarePublicKey(spec, it, accountId) }
        }
    }
}
