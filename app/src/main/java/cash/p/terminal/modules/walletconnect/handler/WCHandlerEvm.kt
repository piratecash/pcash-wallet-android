package cash.p.terminal.modules.walletconnect.handler

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmSignerFactory
import cash.p.terminal.wallet.Account
import com.reown.android.Core
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import kotlinx.coroutines.runBlocking

class WCHandlerEvm(
    private val evmBlockchainManager: EvmBlockchainManager,
    private val evmSignerFactory: EvmSignerFactory
) : IWCHandler {
    private val supportedEvmChains =
        EvmBlockchainManager.blockchainTypes.map { evmBlockchainManager.getChain(it) }

    override val chainNamespace = "eip155"

    override val supportedChains = supportedEvmChains.map { "${chainNamespace}:${it.id}" }
    override val supportedMethods = listOf(
        "eth_sendTransaction",
        "personal_sign",
//        "eth_accounts",
//        "eth_requestAccounts",
//        "eth_call",
//        "eth_getBalance",
//        "eth_sendRawTransaction",
        "eth_sign",
        "eth_signTransaction",
        "eth_signTypedData",
        "eth_signTypedData_v4",
        "wallet_addEthereumChain",
        "wallet_switchEthereumChain"
    )

    override val supportedEvents =
        listOf("chainChanged", "accountsChanged", "connect", "disconnect", "message")

    override fun getAccountAddresses(account: Account): List<String> {
        return supportedEvmChains.mapNotNull { evmChain ->
            val address = getEvmAddress(account, evmChain) ?: return@mapNotNull null

            "${chainNamespace}:${evmChain.id}:${address.eip55}"
        }
    }

    override fun getMethodData(method: String, chainInternalId: String?): MethodData {
        val evmChain = supportedEvmChains.firstOrNull { it.id == chainInternalId?.toInt() }

        val title = when (method) {
            "personal_sign" -> "Personal Sign Request"
            "eth_sign" -> "Standard Sign Request"
            "eth_signTypedData" -> "Typed Sign Request"
            "eth_sendTransaction" -> "Approve Transaction"
            "eth_signTransaction" -> "Sign Transaction"
            else -> method
        }

        return MethodData(title, evmChain?.name ?: "")
    }

    override fun getAction(
        request: Wallet.Model.SessionRequest.JSONRPCRequest,
        peerMetaData: Core.Model.AppMetaData?,
        chainInternalId: String?,
    ) = when (request.method) {
        else -> throw UnsupportedMethodException(request.method)
    }

    private fun getEvmAddress(account: Account, chain: Chain): Address? =
        runBlocking { evmSignerFactory.resolveAddress(account, chain.toBlockchainType(), chain) }

    override fun getChainName(chainInternalId: String): String? {
        val evmChainId = chainInternalId.toInt()

        return supportedEvmChains.find { it.id == evmChainId }?.name
    }

}
