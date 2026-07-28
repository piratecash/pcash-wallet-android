package cash.p.terminal.core.usecase

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezor.domain.usecase.ICreateTrezorWalletUseCase
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorKeyResult
import cash.p.terminal.trezorkit.client.TrezorPublicKeyRequest
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

internal class CreateTrezorWalletUseCase(
    private val trezorClient: ITrezorClient,
    private val accountManager: IAccountManager,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val dispatcherProvider: DispatcherProvider,
    private val accountFactory: IAccountFactory,
    private val walletActivator: WalletActivator
) : ICreateTrezorWalletUseCase {

    override suspend fun invoke(accountName: String): AccountType.TrezorDevice {
        // Read device features and all default public keys in a single session so USB asks for the PIN once.
        val read = trezorClient.connect {
            val features = getFeatures()
            val model = TrezorModel.fromInternalModel(features.internalModel)
            val defaultTokens = TrezorModelSupport.filterByFirmwareCapabilities(
                TrezorModelSupport.getDefaultTokenQueries(model),
                features
            )
            val specs = TrezorPublicKeySpecs.buildQuerySpecs(defaultTokens)
            val uniqueRequests = specs.map { it.request }.distinct()
            val results = getPublicKeys(uniqueRequests)
            DeviceRead(features, defaultTokens, specs, uniqueRequests.zip(results).toMap())
        }

        val accountType = AccountType.TrezorDevice(
            deviceId = read.features.deviceId ?: "unknown",
            // Persist the raw reported internal model (e.g. "T2B1"/"T3B1"); it round-trips through
            // TrezorModel.fromInternalModel when the device is offline.
            model = read.features.internalModel ?: "unknown",
            firmwareVersion = read.features.firmwareVersion,
            walletPublicKey = ""
        )

        val account = accountFactory.account(
            name = accountName,
            type = accountType,
            origin = AccountOrigin.Created,
            backedUp = false,
            fileBackedUp = false
        )

        val publicKeys = read.specs.mapNotNull { spec ->
            read.resultByRequest[spec.request]?.let {
                TrezorPublicKeySpecs.toHardwarePublicKey(spec, it, account.id)
            }
        }
        check(publicKeys.size == read.defaultTokens.size) {
            "Failed to fetch Trezor public keys for all default wallets"
        }

        accountManager.save(account = account, updateActive = false)

        withContext(dispatcherProvider.io) {
            hardwarePublicKeyStorage.save(publicKeys)
        }

        val activatableTokens = read.defaultTokens.filter { token ->
            publicKeys.any { it.blockchainType == token.blockchainType.uid && it.tokenType == token.tokenType }
        }
        walletActivator.activateWalletsSuspended(account, activatableTokens)

        accountManager.setActiveAccountId(account.id)
        return accountType
    }

    private data class DeviceRead(
        val features: TrezorFeatures,
        val defaultTokens: List<TokenQuery>,
        val specs: List<TrezorPublicKeySpecs.QuerySpec>,
        val resultByRequest: Map<TrezorPublicKeyRequest, TrezorKeyResult>
    )
}
