package cash.p.terminal.core.usecase

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.MoneroDeviceWalletProvisioner
import cash.p.terminal.core.managers.MoneroTrezorReadiness
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezor.domain.usecase.ICreateTrezorWalletUseCase
import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightProvider
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorKeyResult
import cash.p.terminal.trezorkit.client.TrezorPublicKeyRequest
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAccountsStorage
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.monero
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class CreateTrezorWalletUseCase(
    private val trezorClient: ITrezorClient,
    private val accountManager: IAccountManager,
    private val accountsStorage: IAccountsStorage,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val dispatcherProvider: DispatcherProvider,
    private val accountFactory: IAccountFactory,
    private val walletActivator: WalletActivator,
    private val moneroReadiness: MoneroTrezorReadiness,
    private val moneroProvisioner: MoneroDeviceWalletProvisioner,
    private val restoreSettingsManager: RestoreSettingsManager,
) : ICreateTrezorWalletUseCase {

    override suspend fun invoke(
        accountName: String,
        moneroRestoreHeightProvider: TrezorMoneroRestoreHeightProvider,
    ): AccountType.TrezorDevice {
        val read = readDevice()
        val accountType = read.accountType()
        val account = accountFactory.account(
            name = accountName,
            type = accountType,
            origin = AccountOrigin.Created,
            backedUp = false,
            fileBackedUp = false,
        )
        val publicKeys = read.publicKeys(account.id)

        return if (read.model == TrezorModel.Safe5) {
            provisionSafe5(
                account = account,
                read = read,
                publicKeys = publicKeys,
                moneroRestoreHeightProvider = moneroRestoreHeightProvider,
            )
            accountType
        } else {
            persistStandardAccount(account, read.publicKeyTokens, publicKeys)
            accountType
        }
    }

    private suspend fun readDevice(): DeviceRead =
        trezorClient.connect {
            val features = getFeatures()
            val model = TrezorModel.fromInternalModel(features.internalModel)
            val publicKeyTokens = TrezorModelSupport.filterByFirmwareCapabilities(
                TrezorModelSupport.getDefaultTokenQueries(model),
                features,
            )
            val specs = TrezorPublicKeySpecs.buildQuerySpecs(publicKeyTokens)
            val requests = (
                specs.map { it.request } +
                    listOfNotNull(
                        TrezorPublicKeySpecs.walletIdentityRequest.takeIf {
                            model == TrezorModel.Safe5
                        },
                    )
                ).distinct()
            DeviceRead(
                features = features,
                model = model,
                publicKeyTokens = publicKeyTokens,
                specs = specs,
                resultByRequest = requests.zip(getPublicKeys(requests)).toMap(),
            )
        }

    private suspend fun provisionSafe5(
        account: Account,
        read: DeviceRead,
        publicKeys: List<HardwarePublicKey>,
        moneroRestoreHeightProvider: TrezorMoneroRestoreHeightProvider,
    ) {
        moneroReadiness.requireSupported(read.features)
        val walletPublicKey = (account.type as AccountType.TrezorDevice).walletPublicKey
        val restoreHeight = savedMoneroRestoreHeight(walletPublicKey)
            ?: moneroRestoreHeightProvider.getRestoreHeight()
        require(restoreHeight >= 0) { "Monero restore height must be non-negative" }
        var accountSaved = false
        try {
            moneroProvisioner.provision(
                account = account,
                restoreHeight = restoreHeight,
                onWalletCreated = {
                    accountManager.save(account = account, updateActive = false)
                    accountSaved = true
                },
            )
            persistAccountData(account, read.publicKeyTokens + TokenQuery.monero, publicKeys)
        } catch (error: Throwable) {
            if (accountSaved) {
                withContext(NonCancellable) {
                    try {
                        accountManager.delete(account.id)
                    } catch (cleanupError: Throwable) {
                        error.addSuppressed(cleanupError)
                    }
                }
            }
            throw error
        }
    }

    private fun savedMoneroRestoreHeight(walletPublicKey: String): Long? {
        if (walletPublicKey.isBlank()) return null

        restoreSettingsManager.trezorMoneroRestoreHeight(walletPublicKey)?.let {
            return it
        }

        return accountManager.getDeletedAccountIds()
            .asSequence()
            .mapNotNull(accountsStorage::loadAccount)
            .filter { deletedAccount ->
                (deletedAccount.type as? AccountType.TrezorDevice)?.walletPublicKey ==
                    walletPublicKey
            }
            .mapNotNull { deletedAccount ->
                restoreSettingsManager.settings(
                    deletedAccount,
                    BlockchainType.Monero,
                ).birthdayHeight?.takeIf { it >= 0 }
            }
            .minOrNull()
    }

    private suspend fun persistStandardAccount(
        account: Account,
        tokenQueries: List<TokenQuery>,
        publicKeys: List<HardwarePublicKey>,
    ) {
        accountManager.save(account = account, updateActive = false)
        persistAccountData(account, tokenQueries, publicKeys)
    }

    private suspend fun persistAccountData(
        account: Account,
        tokenQueries: List<TokenQuery>,
        publicKeys: List<HardwarePublicKey>,
    ) {
        withContext(dispatcherProvider.io) {
            hardwarePublicKeyStorage.save(publicKeys)
        }
        val activatableTokens = tokenQueries.filter { token ->
            token == TokenQuery.monero ||
                publicKeys.any {
                    it.blockchainType == token.blockchainType.uid &&
                        it.tokenType == token.tokenType
                }
        }
        walletActivator.activateWalletsSuspended(account, activatableTokens)
        accountManager.setActiveAccountId(account.id)
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }

    private data class DeviceRead(
        val features: TrezorFeatures,
        val model: TrezorModel?,
        val publicKeyTokens: List<TokenQuery>,
        private val specs: List<TrezorPublicKeySpecs.QuerySpec>,
        private val resultByRequest: Map<TrezorPublicKeyRequest, TrezorKeyResult>,
    ) {
        fun accountType(): AccountType.TrezorDevice =
            AccountType.TrezorDevice(
                deviceId = features.deviceId ?: UNKNOWN,
                model = features.internalModel ?: UNKNOWN,
                firmwareVersion = features.firmwareVersion,
                walletPublicKey =
                    resultByRequest[TrezorPublicKeySpecs.walletIdentityRequest]?.key.orEmpty(),
            )

        fun publicKeys(accountId: String): List<HardwarePublicKey> {
            val keys = specs.mapNotNull { spec ->
                resultByRequest[spec.request]?.let { result ->
                    TrezorPublicKeySpecs.toHardwarePublicKey(spec, result, accountId)
                }
            }
            check(keys.size == specs.size) {
                "Failed to fetch Trezor public keys for all default wallets"
            }
            return keys
        }
    }
}
