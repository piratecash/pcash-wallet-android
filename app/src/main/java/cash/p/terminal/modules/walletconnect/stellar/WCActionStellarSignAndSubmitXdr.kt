package cash.p.terminal.modules.walletconnect.stellar

import cash.p.terminal.core.App
import cash.p.terminal.R
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceFactory
import cash.p.terminal.modules.sendevmtransaction.SectionViewItem
import cash.p.terminal.modules.walletconnect.request.AbstractWCAction
import cash.p.terminal.modules.walletconnect.request.WCActionState
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.modules.sendevmtransaction.ViewItem
import com.google.gson.GsonBuilder
import io.horizontalsystems.core.entities.BlockchainType

import io.horizontalsystems.stellarkit.StellarKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WCActionStellarSignAndSubmitXdr(
    private val paramsJsonStr: String,
    private val peerName: String,
    private val stellarKit: StellarKit,
) : AbstractWCAction() {

    private val gson = GsonBuilder().create()
    private val params = gson.fromJson(paramsJsonStr, Params::class.java)
    private val xdr = params.xdr

    private val token = App.marketKit.token(TokenQuery(BlockchainType.Stellar, TokenType.Native))!!
    private val sendTransactionService = SendTransactionServiceFactory.create(token)
    private var sendTransactionState = sendTransactionService.stateFlow.value

    override fun start(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            sendTransactionService.stateFlow.collect { transactionState ->
                sendTransactionState = transactionState

                emitState()
            }
        }

        coroutineScope.launch {
            sendTransactionService.setSendTransactionData(
                SendTransactionData.Stellar.WithTransactionEnvelope(xdr)
            )
        }
    }

    override fun getTitle(): TranslatableString {
        return TranslatableString.ResString(R.string.WalletConnect_SendTransactionRequest_Title)
    }

    override fun getApproveButtonTitle(): TranslatableString {
        return TranslatableString.ResString(R.string.WalletConnect_SendTransactionRequest_ButtonSend)
    }

    override suspend fun performAction(): String {
        sendTransactionService.sendTransaction()

        return gson.toJson(mapOf("status" to "success"))
    }

    override fun createState(): WCActionState {
        val transaction = stellarKit.getTransaction(xdr)

        var sectionViewItems = WCStellarHelper.getTransactionViewItems(transaction, xdr, peerName)
        sendTransactionState.networkFee?.let { networkFee ->
            sectionViewItems += SectionViewItem(
                listOf(ViewItem.Fee(networkFee))
            )
        }

        return WCActionState(
            runnable = sendTransactionState.sendable,
            items = sectionViewItems
        )
    }

    data class Params(val xdr: String)
}

