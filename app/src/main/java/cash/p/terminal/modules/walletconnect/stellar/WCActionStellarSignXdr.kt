package cash.p.terminal.modules.walletconnect.stellar

import cash.p.terminal.modules.walletconnect.request.AbstractWCAction
import com.google.gson.GsonBuilder
import cash.p.terminal.R
import cash.p.terminal.modules.walletconnect.request.WCActionState
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.stellarkit.StellarKit
import kotlinx.coroutines.CoroutineScope

class WCActionStellarSignXdr(
    private val paramsJsonStr: String,
    private val peerName: String,
    private val stellarKit: StellarKit,
) : AbstractWCAction() {

    private val gson = GsonBuilder().create()
    private val params = gson.fromJson(paramsJsonStr, Params::class.java)
    private val xdr = params.xdr

    override fun getTitle(): TranslatableString {
        return TranslatableString.ResString(R.string.WalletConnect_SignMessageRequest_Title)
    }

    override fun getApproveButtonTitle(): TranslatableString {
        return TranslatableString.ResString(R.string.Button_Sign)
    }

    override suspend fun performAction(): String {
        return gson.toJson(mapOf("signedXDR" to stellarKit.signTransaction(params.xdr)))
    }

    override fun start(coroutineScope: CoroutineScope) = Unit

    override fun createState(): WCActionState {
        val transaction = stellarKit.getTransaction(xdr)

        return WCActionState(
            runnable = true,
            items = WCStellarHelper.getTransactionViewItems(transaction, xdr, peerName)
        )
    }

    data class Params(val xdr: String)
}
