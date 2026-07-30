package cash.p.terminal.trezor.domain

object TrezorAccountIdentityValidator {
    fun matchesDevice(expectedDeviceId: String, liveDeviceId: String?): Boolean =
        expectedDeviceId == UNKNOWN_DEVICE_ID || expectedDeviceId == liveDeviceId

    fun matchesWallet(expectedWalletPublicKey: String, liveWalletPublicKey: String): Boolean =
        expectedWalletPublicKey.isEmpty() || expectedWalletPublicKey == liveWalletPublicKey

    private const val UNKNOWN_DEVICE_ID = "unknown"
}
