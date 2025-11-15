package cash.p.terminal.core.converters

import cash.p.terminal.core.providers.PendingAccountProvider
import cash.p.terminal.entities.PendingTransactionEntity
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import java.math.BigDecimal

class PendingTransactionConverter(
    private val accountProvider: PendingAccountProvider
) {
    fun convert(entity: PendingTransactionEntity, token: Token): PendingTransactionRecord {
        val amount = BigDecimal(entity.amountAtomic).movePointLeft(token.decimals)

        return PendingTransactionRecord(
            uid = entity.id,
            transactionHash = entity.txHash.orEmpty(),
            timestamp = entity.createdAt/1000, // convert to seconds
            source = TransactionSource(
                blockchain = token.blockchain,
                account = accountProvider.fromWalletId(entity.walletId),
                meta = entity.meta
            ),
            token = token,
            amount = amount,
            toAddress = entity.toAddress,
            fromAddress = entity.fromAddress,
            expiresAt = entity.expiresAt,
            memo = entity.memo
        )
    }
}
