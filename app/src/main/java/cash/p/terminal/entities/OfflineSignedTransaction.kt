package cash.p.terminal.entities

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
)

enum class OfflineSignedTransactionStatus(val value: String) {
    Pending("pending"),
    Broadcasted("broadcasted");

    companion object {
        fun from(value: String): OfflineSignedTransactionStatus =
            entries.firstOrNull { it.value == value } ?: Pending
    }
}

// Result of decoding a pcash:tx:v1 payload back into its parts. Atomic amounts are kept as
// strings because the target token's decimals are resolved by the caller, not the payload.
data class DecodedOfflineTransaction(
    val blockchainUid: String,
    val rawHex: String,
    val txHash: String,
    val amountAtomic: String,
    val feeAtomic: String?,
    val toAddress: String,
    val createdAt: Long,
    val inputOutpoints: List<OfflineTransactionOutpoint>,
)
