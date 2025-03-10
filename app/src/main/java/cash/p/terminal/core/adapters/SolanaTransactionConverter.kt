package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.SolanaKitWrapper
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.solana.SolanaIncomingTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaOutgoingTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaUnknownTransactionRecord
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.solanakit.models.FullTransaction
import java.math.BigDecimal

class SolanaTransactionConverter(
    private val coinManager: ICoinManager,
    private val source: TransactionSource,
    private val baseToken: Token,
    private val spamManager: SpamManager,
    solanaKitWrapper: SolanaKitWrapper
) {
    private val userAddress = solanaKitWrapper.solanaKit.receiveAddress

    fun transactionRecord(fullTransaction: FullTransaction): SolanaTransactionRecord {
        val transaction = fullTransaction.transaction
        val incomingTransfers = mutableListOf<SolanaTransactionRecord.Transfer>()
        val outgoingTransfers = mutableListOf<SolanaTransactionRecord.Transfer>()

        transaction.amount?.let {
            if (transaction.from == userAddress) {
                val transactionValue = TransactionValue.CoinValue(
                    baseToken,
                    it.multiply(BigDecimal.valueOf(-1)).movePointLeft(baseToken.decimals)
                )
                outgoingTransfers.add(
                    SolanaTransactionRecord.Transfer(
                        transaction.to,
                        transactionValue
                    )
                )
            } else if (transaction.to == userAddress) {
                val transactionValue =
                    TransactionValue.CoinValue(baseToken, it.movePointLeft(baseToken.decimals))
                incomingTransfers.add(
                    SolanaTransactionRecord.Transfer(
                        transaction.from,
                        transactionValue
                    )
                )
            } else {
            }
        }

        for (fullTokenTransfer in fullTransaction.tokenTransfers) {
            val tokenTransfer = fullTokenTransfer.tokenTransfer
            val mintAccount = fullTokenTransfer.mintAccount
            val query = TokenQuery(BlockchainType.Solana, TokenType.Spl(tokenTransfer.mintAddress))
            val token = coinManager.getToken(query)

            val transactionValue = when {
                token != null -> TransactionValue.CoinValue(
                    token,
                    tokenTransfer.amount.movePointLeft(token.decimals)
                )

                mintAccount.isNft -> TransactionValue.NftValue(
                    NftUid.Solana(mintAccount.address),
                    tokenTransfer.amount,
                    mintAccount.name,
                    mintAccount.symbol
                )

                else -> TransactionValue.RawValue(value = tokenTransfer.amount.toBigInteger())
            }

            if (tokenTransfer.incoming) {
                incomingTransfers.add(
                    SolanaTransactionRecord.Transfer(
                        fullTransaction.transaction.from,
                        transactionValue
                    )
                )
            } else {
                outgoingTransfers.add(
                    SolanaTransactionRecord.Transfer(
                        fullTransaction.transaction.to,
                        transactionValue
                    )
                )
            }
        }

        return when {
            (incomingTransfers.size == 1 && outgoingTransfers.isEmpty()) -> {
                val transfer = incomingTransfers.first()
                SolanaIncomingTransactionRecord(
                    transaction = transaction,
                    baseToken = baseToken,
                    source = source,
                    from = transfer.address,
                    value = transfer.value,
                    isSpam = spamManager.isSpam(
                        incomingEvents = incomingTransfers.map {
                            TransferEvent(it.address, it.value)
                        }, outgoingEvents = outgoingTransfers.map {
                            TransferEvent(it.address, it.value)
                        })
                )
            }

            (incomingTransfers.isEmpty() && outgoingTransfers.size == 1) -> {
                val transfer = outgoingTransfers.first()
                SolanaOutgoingTransactionRecord(
                    transaction = transaction,
                    baseToken = baseToken,
                    source = source,
                    to = transfer.address,
                    value = transfer.value,
                    sentToSelf = transfer.address == userAddress
                )
            }

            else -> SolanaUnknownTransactionRecord(
                transaction = transaction,
                baseToken = baseToken,
                source = source,
                incomingTransfers = incomingTransfers,
                outgoingTransfers = outgoingTransfers,
                isSpam = spamManager.isSpam(
                    incomingEvents = incomingTransfers.map {
                        TransferEvent(it.address, it.value)
                    }, outgoingEvents = outgoingTransfers.map {
                        TransferEvent(it.address, it.value)
                    })
            )
        }
    }

}
