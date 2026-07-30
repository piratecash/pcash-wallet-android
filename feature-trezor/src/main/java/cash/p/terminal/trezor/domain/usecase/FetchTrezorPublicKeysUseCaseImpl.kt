package cash.p.terminal.trezor.domain.usecase

import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.client.TrezorKeyValidationException
import cash.p.terminal.trezor.domain.TrezorAccountIdentityValidator
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.Account
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
    ): List<HardwarePublicKey> {
        if (tokenQueries.isEmpty()) return emptyList()
        val account = checkNotNull(accountManager.account(accountId)) {
            "Trezor account not found"
        }
        val accountType = account.type as? AccountType.TrezorDevice
            ?: error("Trezor public keys require a Trezor account")
        return trezorClient.connect {
            val features = getFeatures()
            requireExpectedDevice(accountType, features)
            val healedAccount = healPersistedModel(account, features)
            // Firmware capability is only known from a live device, so gate Tron here rather than at the caller.
            val derivable = TrezorModelSupport.filterByFirmwareCapabilities(tokenQueries, features)
            val specs = TrezorPublicKeySpecs.buildQuerySpecs(derivable)
            if (specs.isEmpty()) return@connect emptyList()

            // Distinct requests collapse coins that share a derivation (e.g. all EVM chains) into one
            // on-device read; every query still gets its own HardwarePublicKey below.
            val uniqueRequests = (
                specs.map { it.request } + TrezorPublicKeySpecs.walletIdentityRequest
                ).distinct()
            val resultByRequest = uniqueRequests.zip(getPublicKeys(uniqueRequests)).toMap()
            val liveWalletPublicKey =
                resultByRequest[TrezorPublicKeySpecs.walletIdentityRequest]?.key.orEmpty()
            requireExpectedWallet(accountType, liveWalletPublicKey)
            persistWalletIdentity(healedAccount, liveWalletPublicKey)

            specs.mapNotNull { spec ->
                resultByRequest[spec.request]?.let {
                    TrezorPublicKeySpecs.toHardwarePublicKey(spec, it, accountId)
                }
            }
        }
    }

    /**
     * Re-persists the real internal model for legacy accounts whose stored model is unresolvable
     * (early Safe 3 devices saved `"unknown"` before the model enum knew `T2B1`). Without this the
     * account resolves to a null model and Manage Wallets hides Tron/Solana. Fail closed: heal only
     * when the connected device is provably this account's own and reports a model we recognize.
     */
    private fun healPersistedModel(account: Account, features: TrezorFeatures): Account {
        val trezorType = account.type as? AccountType.TrezorDevice ?: return account
        if (TrezorModel.fromInternalModel(trezorType.model) != null) return account
        val reported = features.internalModel ?: return account
        if (TrezorModel.fromInternalModel(reported) == null) return account
        return account.copy(type = trezorType.copy(model = reported))
            .also(accountManager::update)
    }

    private fun persistWalletIdentity(account: Account, liveWalletPublicKey: String) {
        check(liveWalletPublicKey.isNotEmpty()) {
            "Trezor did not return a wallet identity"
        }
        val latestAccount = accountManager.account(account.id) ?: account
        val accountType = latestAccount.type as? AccountType.TrezorDevice
            ?: error("Trezor public keys require a Trezor account")
        requireExpectedWallet(accountType, liveWalletPublicKey)
        if (accountType.walletPublicKey.isEmpty()) {
            accountManager.update(
                latestAccount.copy(type = accountType.copy(walletPublicKey = liveWalletPublicKey)),
            )
        }
    }

    private fun requireExpectedDevice(
        accountType: AccountType.TrezorDevice,
        features: TrezorFeatures,
    ) {
        if (!TrezorAccountIdentityValidator.matchesDevice(accountType.deviceId, features.deviceId)) {
            throw TrezorKeyValidationException("Connected Trezor does not match the account")
        }
    }

    private fun requireExpectedWallet(
        accountType: AccountType.TrezorDevice,
        liveWalletPublicKey: String,
    ) {
        if (
            !TrezorAccountIdentityValidator.matchesWallet(
                accountType.walletPublicKey,
                liveWalletPublicKey,
            )
        ) {
            throw TrezorKeyValidationException(
                "Connected Trezor passphrase wallet does not match the account",
            )
        }
    }
}
