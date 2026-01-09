package cash.p.terminal.entities

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.meta
import java.math.BigDecimal
import java.util.UUID

data class PendingTransactionDraft(
    val id: String = UUID.randomUUID().toString(),
    val wallet: Wallet,
    val token: Token,
    val amount: BigDecimal,
    val fee: BigDecimal?,
    val sdkBalanceAtCreation: BigDecimal,
    val fromAddress: String,
    val toAddress: String,
    val meta: String? = token.type.meta,
    val memo: String? = null,
    val txHash: String? = null,
    val nonce: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
