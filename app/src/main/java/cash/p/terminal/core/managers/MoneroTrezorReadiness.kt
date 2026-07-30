package cash.p.terminal.core.managers

import cash.p.terminal.trezor.domain.TrezorMoneroAdmissionFailure
import cash.p.terminal.trezor.domain.TrezorMoneroAdmissionPolicy
import cash.p.terminal.trezor.domain.TrezorAccountIdentityValidator
import cash.p.terminal.trezorkit.TrezorCancelledException
import cash.p.terminal.trezorkit.TrezorNotInitializedException
import cash.p.terminal.trezorkit.TrezorUsbAcquireTimeoutException
import cash.p.terminal.trezorkit.TrezorUsbDeviceNotFoundException
import cash.p.terminal.trezorkit.TrezorUsbDisconnectedException
import cash.p.terminal.trezorkit.TrezorUsbInterfaceUnavailableException
import cash.p.terminal.trezorkit.TrezorUsbOpenFailedException
import cash.p.terminal.trezorkit.TrezorUsbOperationTimeoutException
import cash.p.terminal.trezorkit.TrezorUsbPermissionDeniedException
import cash.p.terminal.trezorkit.TrezorUsbShortPacketException
import cash.p.terminal.trezorkit.TrezorUsbStaleChannelException
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorSessionId
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException

internal class MoneroTrezorReadiness(
    private val identityValidator: TrezorAccountIdentityValidator,
) {
    fun requireLive(
        features: TrezorFeatures,
        accountType: AccountType.TrezorDevice?,
    ): TrezorFeatures {
        requireSupported(features)
        if (
            accountType != null &&
            !identityValidator.matchesDevice(accountType.deviceId, features.deviceId)
        ) {
            throw HardwareWalletOperationException(
                HardwareWalletErrorCode.WrongDevice,
                "Connected Trezor does not match the account",
            )
        }
        return features
    }

    fun requireSupported(features: TrezorFeatures) {
        if (TrezorMoneroAdmissionPolicy.liveFailure(features) != null) {
            throw HardwareWalletOperationException(
                admissionError(features),
                "Trezor Monero admission failed",
            )
        }
    }

    suspend fun requireWallet(
        account: Account,
        liveWalletPublicKey: String,
    ) {
        if (!identityValidator.matchesWallet(account, liveWalletPublicKey)) {
            throw HardwareWalletOperationException(
                HardwareWalletErrorCode.WrongWallet,
                "Connected Trezor passphrase wallet does not match the account",
            )
        }
    }

    fun requireSession(features: TrezorFeatures): TrezorSessionId =
        features.takeSessionId() ?: throw HardwareWalletOperationException(
            HardwareWalletErrorCode.Protocol,
            "Trezor did not return a resumable session ID",
        )

    private fun admissionError(features: TrezorFeatures): HardwareWalletErrorCode =
        when (TrezorMoneroAdmissionPolicy.liveFailure(features)) {
            TrezorMoneroAdmissionFailure.UnsupportedModel ->
                HardwareWalletErrorCode.UnsupportedModel
            TrezorMoneroAdmissionFailure.DeviceNotInitialized ->
                HardwareWalletErrorCode.DeviceNotInitialized
            TrezorMoneroAdmissionFailure.CapabilityMissing ->
                HardwareWalletErrorCode.CapabilityMissing
            TrezorMoneroAdmissionFailure.FirmwareUnsupported ->
                HardwareWalletErrorCode.FirmwareUnsupported
            null -> HardwareWalletErrorCode.Protocol
        }

    fun hardwareFailure(error: Throwable): HardwareWalletOperationException {
        return HardwareWalletOperationException(error.hardwareWalletErrorCode(), error.message)
    }
}

internal fun Throwable.hardwareWalletErrorCode(): HardwareWalletErrorCode =
    when (this) {
        is TrezorCancelledException -> HardwareWalletErrorCode.Cancelled
        is TrezorUsbDeviceNotFoundException -> HardwareWalletErrorCode.DeviceNotFound
        is TrezorUsbPermissionDeniedException -> HardwareWalletErrorCode.PermissionDenied
        is TrezorUsbOpenFailedException -> HardwareWalletErrorCode.UsbOpenFailed
        is TrezorUsbInterfaceUnavailableException ->
            HardwareWalletErrorCode.UsbInterfaceUnavailable
        is TrezorUsbAcquireTimeoutException -> HardwareWalletErrorCode.AcquireTimeout
        is TrezorUsbOperationTimeoutException -> HardwareWalletErrorCode.PacketTimeout
        is TrezorUsbDisconnectedException -> HardwareWalletErrorCode.Disconnected
        is TrezorUsbStaleChannelException -> HardwareWalletErrorCode.StaleLease
        is TrezorUsbShortPacketException -> HardwareWalletErrorCode.ShortPacket
        is TrezorNotInitializedException -> HardwareWalletErrorCode.DeviceNotInitialized
        else -> HardwareWalletErrorCode.Protocol
    }
