package cash.p.terminal.modules.walletconnect

import cash.p.terminal.core.App
import cash.p.terminal.modules.walletconnect.handler.IWCHandler
import cash.p.terminal.modules.walletconnect.handler.MethodData
import cash.p.terminal.modules.walletconnect.request.AbstractWCAction
import cash.p.terminal.modules.walletconnect.request.WCChainData
import cash.p.terminal.modules.walletconnect.session.ValidationError
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.entities.BlockchainType

class WCManager(
    private val accountManager: IAccountManager,
) {
    sealed class SupportState {
        object Supported : SupportState()
        object NotSupportedDueToNoActiveAccount : SupportState()
        data class NotSupportedDueToNonBackedUpAccount(val account: Account) : SupportState()
        object NotSupported : SupportState()
    }

    private val handlersMap = mutableMapOf<String, IWCHandler>()

    fun addWcHandler(wcHandler: IWCHandler) {
        handlersMap[wcHandler.chainNamespace] = wcHandler
    }

    fun getMethodData(sessionRequest: Wallet.Model.SessionRequest): MethodData? {
        val chainId = sessionRequest.chainId ?: return null
        val chainParts = chainId.split(":")

        val chainNamespace = chainParts.getOrNull(0)
        val chainInternalId = chainParts.getOrNull(1)

        val handler = handlersMap[chainNamespace] ?: return null

        return handler.getMethodData(sessionRequest.request.method, chainInternalId)
    }

    fun getActionForRequest(sessionRequest: Wallet.Model.SessionRequest?): AbstractWCAction? {
        if (sessionRequest == null) return null
        val chainId = sessionRequest.chainId ?: return null
        val chainParts = chainId.split(":")

        val chainNamespace = chainParts.getOrNull(0)
        val chainInternalId = chainParts.getOrNull(1)

        val handler = handlersMap[chainNamespace] ?: return null

        return handler.getAction(sessionRequest.request, sessionRequest.peerMetaData, chainInternalId)
    }

    fun getWalletConnectSupportState(): SupportState {
        val tmpAccount = accountManager.activeAccount
        return when {
            tmpAccount == null -> SupportState.NotSupportedDueToNoActiveAccount
            !tmpAccount.type.supportsWalletConnect -> SupportState.NotSupported
            tmpAccount.accountSupportsBackup && !tmpAccount.isBackedUp && !tmpAccount.isFileBackedUp -> SupportState.NotSupportedDueToNonBackedUpAccount(
                tmpAccount
            )
            else -> SupportState.NotSupported
        }
    }

    fun getBlockchainType(sessionChainId: String?): BlockchainType? {
        val chainId = getChainData(sessionChainId)?.id
        return chainId?.let { App.evmBlockchainManager.getBlockchain(it) }?.type
    }

    fun getChainData(chainId: String?): WCChainData? {
        return WCUtils.getChainData(chainId ?: return null)
    }

    fun validate(requiredNamespaces: Map<String, Wallet.Model.Namespace.Proposal>) {
        requiredNamespaces.forEach { (chainNamespace, proposal) ->
            val handler = handlersMap[chainNamespace]
                ?: throw ValidationError.UnsupportedChainNamespace(chainNamespace)

            proposal.chains?.let { requiredChains ->
                val unsupportedChains = requiredChains - handler.supportedChains
                if (unsupportedChains.isNotEmpty()) {
                    throw ValidationError.UnsupportedChains(unsupportedChains)
                }
            }

            val unsupportedMethods = proposal.methods - handler.supportedMethods
            if (unsupportedMethods.isNotEmpty()) {
                throw ValidationError.UnsupportedMethods(unsupportedMethods)
            }

            val unsupportedEvents = proposal.events - handler.supportedEvents
            if (unsupportedEvents.isNotEmpty()) {
                throw ValidationError.UnsupportedEvents(unsupportedEvents)
            }
        }
    }

    fun getSupportedNamespaces(account: Account) =
        handlersMap.map { (chainNamespace, handler) ->
            chainNamespace to Wallet.Model.Namespace.Session(
                chains = handler.supportedChains,
                methods = handler.supportedMethods,
                events = handler.supportedEvents,
                accounts = handler.getAccountAddresses(account)
            )
        }.toMap()

    fun getChainNames(namespaces: Map<String, Wallet.Model.Namespace.Session>): List<String> {
        val res = mutableListOf<String>()

        for ((chainNamespace, session) in namespaces) {
            val handler = handlersMap[chainNamespace] ?: continue

            for (accountId in session.accounts) {
                val accountIdParts = accountId.split(":")
                val chainInternalId = accountIdParts.getOrNull(1) ?: continue

                handler.getChainName(chainInternalId)?.let {
                    res.add(it)
                }
            }
        }

        return res
    }
}
