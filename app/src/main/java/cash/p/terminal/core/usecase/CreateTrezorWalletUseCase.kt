package cash.p.terminal.core.usecase

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.trezor.domain.TrezorDeepLinkManager
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorMethod
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCase
import cash.p.terminal.trezor.domain.usecase.ICreateTrezorWalletUseCase
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class CreateTrezorWalletUseCase(
    private val deepLinkManager: TrezorDeepLinkManager,
    private val accountManager: IAccountManager,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val dispatcherProvider: DispatcherProvider,
    private val accountFactory: IAccountFactory,
    private val walletActivator: WalletActivator,
    private val fetchPublicKeys: FetchTrezorPublicKeysUseCase
) : ICreateTrezorWalletUseCase {

    override suspend fun invoke(accountName: String): AccountType.TrezorDevice {
        val features = fetchDeviceFeatures()
        val model = TrezorModel.fromInternalModel(features.internalModel)
        val defaultTokens = TrezorModelSupport.getDefaultTokenQueries(model)

        val accountType = AccountType.TrezorDevice(
            deviceId = features.deviceId,
            model = model?.id ?: "unknown",
            firmwareVersion = features.firmwareVersion,
            walletPublicKey = ""
        )

        val account = accountFactory.account(
            name = accountName,
            type = accountType,
            origin = AccountOrigin.Created,
            backedUp = false,
            fileBackedUp = false
        )

        val publicKeys = fetchPublicKeys(defaultTokens, account.id)
        check(publicKeys.size == defaultTokens.size) {
            "Failed to fetch Trezor public keys for all default wallets"
        }

        accountManager.save(account = account, updateActive = false)

        withContext(dispatcherProvider.io) {
            hardwarePublicKeyStorage.save(publicKeys)
        }

        val activatableTokens = defaultTokens.filter { token ->
            publicKeys.any { it.blockchainType == token.blockchainType.uid }
        }
        walletActivator.activateWalletsSuspended(account, activatableTokens)

        accountManager.setActiveAccountId(account.id)
        return accountType
    }

    private suspend fun fetchDeviceFeatures(): DeviceFeatures {
        val response = deepLinkManager.call(TrezorMethod.GetFeatures)
        check(response.success) { "Failed to get Trezor features: ${response.error}" }

        val payload = requireNotNull(response.payload).jsonObject
        val fwMajor = payload["major_version"]?.jsonPrimitive?.content ?: "0"
        val fwMinor = payload["minor_version"]?.jsonPrimitive?.content ?: "0"
        val fwPatch = payload["patch_version"]?.jsonPrimitive?.content ?: "0"

        return DeviceFeatures(
            deviceId = payload["device_id"]?.jsonPrimitive?.content ?: "unknown",
            internalModel = payload["internal_model"]?.jsonPrimitive?.content,
            firmwareVersion = "$fwMajor.$fwMinor.$fwPatch"
        )
    }

    private data class DeviceFeatures(
        val deviceId: String,
        val internalModel: String?,
        val firmwareVersion: String
    )
}
