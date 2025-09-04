package cash.p.terminal.modules.walletconnect.handler

import cash.p.terminal.modules.walletconnect.request.AbstractWCAction
import cash.p.terminal.wallet.Account
import com.reown.android.Core
import com.reown.walletkit.client.Wallet

interface IWCHandler {
    val chainNamespace: String

    val supportedChains: List<String>
    val supportedMethods: List<String>
    val supportedEvents: List<String>

    fun getAction(
        request: Wallet.Model.SessionRequest.JSONRPCRequest,
        peerMetaData: Core.Model.AppMetaData?,
        chainInternalId: String?
    ): AbstractWCAction

    fun getAccountAddresses(account: Account): List<String>

    fun getMethodData(method: String, chainInternalId: String?): MethodData
    fun getChainName(chainInternalId: String): String?
}
