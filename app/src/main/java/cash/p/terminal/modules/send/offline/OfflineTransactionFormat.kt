package cash.p.terminal.modules.send.offline

import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.ui.compose.components.canEncodeAsPcashQrCode

enum class OfflineTransactionFormat {
    Pcash,
    Raw,
}

internal fun OfflineTransactionFormat.content(transaction: OfflineSignedTransaction): String =
    when (this) {
        OfflineTransactionFormat.Pcash -> transaction.pcashPayload
        OfflineTransactionFormat.Raw -> transaction.rawHex
    }

internal fun OfflineTransactionFormat.preferredTransferFormat(transaction: OfflineSignedTransaction): OfflineTransactionFormat =
    when (this) {
        OfflineTransactionFormat.Raw -> OfflineTransactionFormat.Raw
        OfflineTransactionFormat.Pcash -> {
            val pcashContent = OfflineTransactionFormat.Pcash.content(transaction)
            val rawContent = OfflineTransactionFormat.Raw.content(transaction)
            if (!pcashContent.canEncodeAsOfflineQr() &&
                rawContent.length < pcashContent.length &&
                rawContent.canEncodeAsOfflineQr()
            ) {
                OfflineTransactionFormat.Raw
            } else {
                OfflineTransactionFormat.Pcash
            }
        }
    }

internal fun String.canEncodeAsOfflineQr(): Boolean =
    canEncodeAsPcashQrCode(this)
