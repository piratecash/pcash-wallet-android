package cash.p.terminal.modules.send.offline

import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.ui.compose.components.animatedQrFrames
import cash.p.terminal.ui.compose.components.isReadablePcashQrCode

enum class OfflineTransactionFormat {
    Pcash,
    Raw,
}

internal fun OfflineTransactionFormat.content(transaction: OfflineSignedTransaction): String =
    when (this) {
        OfflineTransactionFormat.Pcash -> transaction.pcashPayload
        OfflineTransactionFormat.Raw -> transaction.rawHex
    }

internal fun OfflineTransactionFormat.preferredTransferFormat(
    transaction: OfflineSignedTransaction
): OfflineTransactionFormat =
    when (this) {
        OfflineTransactionFormat.Raw -> OfflineTransactionFormat.Raw
        OfflineTransactionFormat.Pcash -> {
            val pcashContent = OfflineTransactionFormat.Pcash.content(transaction)
            val rawContent = OfflineTransactionFormat.Raw.content(transaction)
            // Ordered by how pleasant the transfer is: one static QR beats an animated tape,
            // and Pcash beats Raw at equal effort because it carries the readable metadata.
            when {
                pcashContent.canEncodeAsOfflineQr() -> OfflineTransactionFormat.Pcash
                rawContent.length < pcashContent.length && rawContent.canEncodeAsOfflineQr() ->
                    OfflineTransactionFormat.Raw

                animatedQrFrames(pcashContent) != null -> OfflineTransactionFormat.Pcash
                animatedQrFrames(rawContent) != null -> OfflineTransactionFormat.Raw
                else -> OfflineTransactionFormat.Pcash
            }
        }
    }

internal fun String.canEncodeAsOfflineQr(): Boolean =
    isReadablePcashQrCode(this)
