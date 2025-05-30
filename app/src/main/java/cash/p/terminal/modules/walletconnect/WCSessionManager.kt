package cash.p.terminal.modules.walletconnect

import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.modules.walletconnect.storage.WCSessionStorage
import cash.p.terminal.modules.walletconnect.storage.WalletConnectV2Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow

class WCSessionManager(
    private val accountManager: cash.p.terminal.wallet.IAccountManager,
    private val storage: WCSessionStorage,
) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _sessionsFlow = MutableStateFlow<List<Wallet.Model.Session>>(emptyList())
    val sessionsFlow: StateFlow<List<Wallet.Model.Session>>
        get() = _sessionsFlow

    private val _pendingRequestCountFlow = MutableStateFlow(0)
    val pendingRequestCountFlow: StateFlow<Int>
        get() = _pendingRequestCountFlow

    val sessions: List<Wallet.Model.Session>
        get() {
            val accountId = accountManager.activeAccount?.id ?: return emptyList()
            return getSessions(accountId)
        }

    fun start() {
        syncSessions()

        coroutineScope.launch {
            accountManager.activeAccountStateFlow.collect { activeAccountState ->
                if (activeAccountState is cash.p.terminal.wallet.ActiveAccountState.ActiveAccount) {
                    syncSessions()
                }
            }
        }

        coroutineScope.launch {
            WCDelegate.pendingRequestEvents.collect {
                syncPendingRequest()
            }
        }

        coroutineScope.launch {
            accountManager.accountsDeletedFlowable.asFlow().collect {
                handleDeletedAccount()
            }
        }

        coroutineScope.launch {
            WCDelegate.walletEvents.collect {
                syncSessions()
            }
        }
    }

    fun getCurrentSessionRequests(): List<Wallet.Model.SessionRequest> {
        val accountId = accountManager.activeAccount?.id ?: return emptyList()
        return requests(accountId)
    }

    private fun syncSessions() {
        val accountId = accountManager.activeAccount?.id ?: return

        val currentSessions = WCDelegate.getActiveSessions()

        val allDbSessions = storage.getAllSessions()
        val allDbTopics = allDbSessions.map { it.topic }

        val newSessions = currentSessions.filter { !allDbTopics.contains(it.topic) }
        val deletedTopics = allDbTopics.filter { topic ->
            !currentSessions.any { it.topic == topic }
        }

        storage.save(newSessions.map { WalletConnectV2Session(accountId, it.topic) })
        storage.deleteSessionsByTopics(deletedTopics)

        _sessionsFlow.update { getSessions(accountId) }
        syncPendingRequest()
    }

    private fun getSessions(accountId: String): List<Wallet.Model.Session> {
        val sessions = Web3Wallet.getListOfActiveSessions()
        val dbSessions = storage.getSessionsByAccountId(accountId)

        val accountSessions = sessions.filter { session ->
            dbSessions.any { it.topic == session.topic }
        }

        return accountSessions
    }

    private fun syncPendingRequest() {
        val requestsCount = accountManager.activeAccount?.let { requests(it.id).size } ?: 0
        _pendingRequestCountFlow.update { requestsCount }
    }

    private fun requests(accountId: String): List<Wallet.Model.SessionRequest> {
        val sessions = getSessions(accountId)
        val pendingRequests = mutableListOf<Wallet.Model.SessionRequest>()
        sessions.forEach { session ->
            pendingRequests.addAll(Web3Wallet.getPendingListOfSessionRequests(session.topic))
        }
        return pendingRequests
    }

    private fun handleDeletedAccount() {
        val existingAccountIds = accountManager.accounts.map { it.id }
        storage.deleteSessionsExcept(accountIds = existingAccountIds)

        syncSessions()
    }

    open class RequestDataError : Throwable() {
        object UnsupportedChainId : RequestDataError()
        object NoSuitableAccount : RequestDataError()
        object NoSuitableEvmKit : RequestDataError()
        object NoSigner : RequestDataError()
        object RequestNotFoundError : RequestDataError()
        object InvalidGasPrice: RequestDataError()
        object InvalidNonce: RequestDataError()
    }

}
