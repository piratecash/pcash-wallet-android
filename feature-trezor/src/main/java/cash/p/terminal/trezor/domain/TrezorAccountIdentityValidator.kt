package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage

class TrezorAccountIdentityValidator(
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
) {
    fun matchesDevice(expectedDeviceId: String, liveDeviceId: String?): Boolean =
        expectedDeviceId == UNKNOWN_DEVICE_ID || expectedDeviceId == liveDeviceId

    suspend fun matchesWallet(account: Account, liveWalletPublicKey: String): Boolean {
        if (liveWalletPublicKey.isEmpty()) return false
        val accountType = account.type as? AccountType.TrezorDevice ?: return false
        val expectedWalletPublicKey = accountType.walletPublicKey.takeIf(String::isNotEmpty)
            ?: storedWalletPublicKey(account.id)
        return expectedWalletPublicKey?.takeIf(String::isNotEmpty) == liveWalletPublicKey
    }

    private suspend fun storedWalletPublicKey(accountId: String): String? {
        val query = TrezorPublicKeySpecs.walletIdentityTokenQuery
        return hardwarePublicKeyStorage.getKey(
            accountId,
            query.blockchainType,
            query.tokenType,
        )?.key?.value
    }

    private companion object {
        const val UNKNOWN_DEVICE_ID = "unknown"
    }
}
