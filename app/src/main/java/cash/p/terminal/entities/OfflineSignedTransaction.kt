package cash.p.terminal.entities

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import java.math.BigDecimal

data class OfflineSignedTransaction(
    val rawHex: String,
    val pcashPayload: String,
    val txHash: String,
    val createdAt: Long,
)

data class OfflineSignedTransactionDraft(
    val wallet: Wallet,
    val amount: BigDecimal,
    val fee: BigDecimal?,
    val toAddress: String,
    val rawHex: String,
    val txHash: String,
    val inputOutpoints: List<OfflineTransactionOutpoint>,
    val createdAt: Long = System.currentTimeMillis(),
    val feeToken: Token? = null,
    val solanaRetryMetadata: OfflineSolanaRetryMetadata? = null,
    val tonRetryMetadata: OfflineTonRetryMetadata? = null,
    val tronRetryMetadata: OfflineTronRetryMetadata? = null,
)

enum class OfflineSignedTransactionStatus(val value: String) {
    Pending("pending"),
    Broadcasted("broadcasted");

    companion object {
        fun from(value: String): OfflineSignedTransactionStatus =
            entries.firstOrNull { it.value == value } ?: Pending
    }
}

data class OfflineTokenMetadata(
    val tokenQueryId: String,
    val coinUid: String?,
    val coinCode: String,
    val coinName: String?,
    val decimals: Int,
)

data class OfflineFeeMetadata(
    val tokenQueryId: String,
    val atomic: String,
    val decimals: Int,
)

data class OfflineSolanaRetryMetadata(
    val blockHash: String,
    val lastValidBlockHeight: Long,
)

data class OfflineTonRetryMetadata(
    val validUntil: Long,
    val senderAddress: String,
    val seqno: Int,
)

data class OfflineTronRetryMetadata(
    val expiration: Long,
)

// Result of decoding a pcash:tx:v1 payload back into its parts.
data class DecodedOfflineTransaction(
    val blockchainUid: String,
    val rawHex: String,
    val txHash: String,
    val token: OfflineTokenMetadata,
    val amountAtomic: String,
    val fee: OfflineFeeMetadata?,
    val toAddress: String,
    val createdAt: Long,
    val inputOutpoints: List<OfflineTransactionOutpoint>,
    val solanaRetryMetadata: OfflineSolanaRetryMetadata? = null,
    val tonRetryMetadata: OfflineTonRetryMetadata? = null,
    val tronRetryMetadata: OfflineTronRetryMetadata? = null,
)
