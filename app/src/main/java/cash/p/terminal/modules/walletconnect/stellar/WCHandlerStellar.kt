package cash.p.terminal.modules.walletconnect.stellar

import cash.p.terminal.core.App
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.modules.walletconnect.handler.IWCHandler
import cash.p.terminal.modules.walletconnect.handler.MethodData
import cash.p.terminal.modules.walletconnect.handler.UnsupportedMethodException
import cash.p.terminal.modules.walletconnect.request.AbstractWCAction
import cash.p.terminal.wallet.Account
import com.walletconnect.android.Core
import com.walletconnect.web3.wallet.client.Wallet
import io.horizontalsystems.stellarkit.StellarKit

class WCHandlerStellar(private val stellarKitManager: StellarKitManager) : IWCHandler {
    override val chainNamespace = "stellar"

    override val supportedChains = listOf("stellar:pubnet")
    override val supportedMethods = listOf("stellar_signAndSubmitXDR", "stellar_signXDR")
    override val supportedEvents = listOf<String>()

    override fun getAction(
        request: Wallet.Model.SessionRequest.JSONRPCRequest,
        peerMetaData: Core.Model.AppMetaData?,
        chainInternalId: String?,
    ): AbstractWCAction {
        val stellarKit = getStellarKit(App.accountManager.activeAccount!!)

        return when (request.method) {
            "stellar_signAndSubmitXDR" -> WCActionStellarSignAndSubmitXdr(
                request.params,
                peerMetaData?.name ?: "",
                stellarKit
            )

            "stellar_signXDR" -> WCActionStellarSignXdr(
                request.params,
                peerMetaData?.name ?: "",
                stellarKit
            )

            else -> throw UnsupportedMethodException(request.method)
        }
    }

    private fun getStellarKit(account: Account): StellarKit {
        return stellarKitManager.getStellarKitWrapper(account).stellarKit
    }

    override fun getAccountAddresses(account: Account): List<String> {
        val address = stellarKitManager.getAddress(account.type)

        return supportedChains.map { "$it:$address" }
    }

    override fun getMethodData(method: String, chainInternalId: String?): MethodData {
        val title = when (method) {
            "stellar_signAndSubmitXDR" -> "Approve Transaction"
            "stellar_signXDR" -> "Sign Request"
            else -> method
        }

        return MethodData(title, "Stellar")
    }

    override fun getChainName(chainInternalId: String) = "Stellar"
}
