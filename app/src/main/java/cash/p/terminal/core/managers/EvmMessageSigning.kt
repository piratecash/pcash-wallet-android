package cash.p.terminal.core.managers

import cash.p.terminal.tangem.signer.HardwareWalletEvmSigner
import cash.p.terminal.trezor.signer.TrezorEvmSigner
import io.horizontalsystems.ethereumkit.core.signer.Signer

/**
 * Single dispatch point for EVM message signing. The base [Signer] exposes only non-suspend,
 * final `signByteArray*`/`signTypedData` methods signed with a mock key for hardware wallets
 * (see [HardwareWalletEvmSigner]/[TrezorEvmSigner]), so hardware signers expose their own
 * suspend counterparts that must be routed here instead of calling the base methods directly.
 */
object EvmMessageSigning {

    suspend fun signPersonalMessage(signer: Signer, message: ByteArray): ByteArray = when (signer) {
        is HardwareWalletEvmSigner -> signer.signPersonalMessage(message)
        is TrezorEvmSigner -> signer.signPersonalMessage(message)
        else -> signer.signByteArray(message)
    }

    suspend fun signLegacyHash(signer: Signer, hash: ByteArray): ByteArray = when (signer) {
        is HardwareWalletEvmSigner -> signer.signLegacyHash(hash)
        is TrezorEvmSigner -> signer.signLegacyHash(hash)
        else -> signer.signByteArrayLegacy(hash)
    }

    suspend fun signTypedData(signer: Signer, rawJson: String): ByteArray = when (signer) {
        is HardwareWalletEvmSigner -> signer.signTypedDataMessage(rawJson)
        is TrezorEvmSigner -> signer.signTypedDataMessage(rawJson)
        else -> signer.signTypedData(rawJson)
    }
}
