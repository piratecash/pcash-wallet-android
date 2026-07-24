package cash.p.terminal.trezor.domain.usecase

import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery

internal class FetchTrezorPublicKeysUseCaseImpl(
    private val trezorClient: ITrezorClient,
    private val accountManager: IAccountManager
) : FetchTrezorPublicKeysUseCase {

    override suspend fun invoke(
        tokenQueries: List<TokenQuery>,
        accountId: String
    ): List<HardwarePublicKey> = trezorClient.connect {
        val features = getFeatures()
        healPersistedModel(accountId, features)
        // Firmware capability is only known from a live device, so gate Tron here rather than at the caller.
        val derivable = TrezorModelSupport.filterByFirmwareCapabilities(tokenQueries, features)
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

    /**
     * Re-persists the real internal model for legacy accounts whose stored model is unresolvable
     * (early Safe 3 devices saved `"unknown"` before the model enum knew `T2B1`). Without this the
     * account resolves to a null model and Manage Wallets hides Tron/Solana. Fail closed: heal only
     * when the connected device is provably this account's own and reports a model we recognize.
     */
    private fun healPersistedModel(accountId: String, features: TrezorFeatures) {
        val account = accountManager.account(accountId) ?: return
        val trezorType = account.type as? AccountType.TrezorDevice ?: return
        if (TrezorModel.fromInternalModel(trezorType.model) != null) return
        val liveDeviceId = features.deviceId ?: return
        if (liveDeviceId != trezorType.deviceId) return
        val reported = features.internalModel ?: return
        if (TrezorModel.fromInternalModel(reported) == null) return
        accountManager.update(account.copy(type = trezorType.copy(model = reported)))
    }
}
