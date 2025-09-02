package cash.p.terminal.modules.walletconnect.stellar

import cash.p.terminal.R
import cash.p.terminal.modules.sendevmtransaction.SectionViewItem
import cash.p.terminal.modules.sendevmtransaction.ValueType
import cash.p.terminal.modules.sendevmtransaction.ViewItem
import cash.p.terminal.strings.helpers.Translator
import org.stellar.sdk.Transaction
import org.stellar.sdk.operations.Operation

object WCStellarHelper {

    fun getTransactionViewItems(transaction: Transaction, xdr: String, peerName: String): List<SectionViewItem> {
        val operationItems = transaction.operations.map { operation: Operation ->
            ViewItem.Value(
                "Operation",
                operation.javaClass.simpleName,
                ValueType.Regular
            )
        }

        return listOf(
            SectionViewItem(
                operationItems + listOf(
                    ViewItem.Input("Transaction XDR", xdr)
                )
            ),
            SectionViewItem(
                listOf(
                    ViewItem.Value(
                        Translator.getString(R.string.WalletConnect_SignMessageRequest_dApp),
                        peerName,
                        ValueType.Regular
                    ),
                    ViewItem.Value(
                        "Stellar",
                        "",
                        ValueType.Regular
                    )
                )
            )
        )
    }
}
