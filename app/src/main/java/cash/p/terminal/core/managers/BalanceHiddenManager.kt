package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BalanceHiddenManager(
    private val localStorage: ILocalStorage,
    backgroundManager: BackgroundManager,
    dispatcherProvider: DispatcherProvider,
) : IBalanceHiddenManager {
    override val balanceHidden: Boolean
        get() = localStorage.balanceHidden

    override val balanceAutoHidden: Boolean
        get() = localStorage.balanceAutoHideEnabled

    private var balanceAutoHide = balanceAutoHidden

    private val _balanceHiddenFlow = MutableStateFlow(localStorage.balanceHidden)
    override val balanceHiddenFlow = _balanceHiddenFlow.asStateFlow()
    private val scope = CoroutineScope(dispatcherProvider.default)

    // Cached flows to prevent memory leaks from creating new flows on each call
    private val walletFlowCache = mutableMapOf<String, StateFlow<Boolean>>()
    private val transactionFlowCache = mutableMapOf<String, StateFlow<Boolean>>()
    private val transactionForWalletFlowCache = mutableMapOf<Pair<String, String>, StateFlow<Boolean>>()

    // Session-scoped toggled wallets - items that differ from global state (not persisted)
    private val _toggledWallets = MutableStateFlow<Set<String>>(emptySet())

    // Session-scoped toggled transactions - items that differ from global state (not persisted)
    private val _toggledTransactions = MutableStateFlow<Set<String>>(emptySet())

    override val anyWalletVisibilityChangedFlow: Flow<Unit> = combine(
        _balanceHiddenFlow,
        _toggledWallets
    ) { }

    override val anyTransactionVisibilityChangedFlow: Flow<Unit> = combine(
        anyWalletVisibilityChangedFlow,
        _toggledTransactions
    ) { }

    init {
        scope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterBackground && balanceAutoHide) {
                    setBalanceHidden(true)
                }
            }
        }

        if (balanceAutoHide) {
            setBalanceHidden(true)
        }
    }

    override fun toggleBalanceHidden() {
        setBalanceHidden(!localStorage.balanceHidden)
    }

    override fun setBalanceAutoHidden(enabled: Boolean) {
        balanceAutoHide = enabled
        localStorage.balanceAutoHideEnabled = enabled

        setBalanceHidden(balanceAutoHide)
    }

    override fun setBalanceHidden(hidden: Boolean) {
        localStorage.balanceHidden = hidden
        _balanceHiddenFlow.update { hidden }

        // Change state of all opened wallets/transactions when global state changes
        _toggledWallets.update { emptySet() }
        _toggledTransactions.update { emptySet() }
    }

    // Wallet-related functions
    override fun walletBalanceHiddenFlow(walletUid: String): StateFlow<Boolean> =
        walletFlowCache.getOrPut(walletUid) {
            combine(
                _balanceHiddenFlow,
                _toggledWallets
            ) { globalHidden, toggledSet ->
                globalHidden != (walletUid in toggledSet)
            }.stateIn(scope, SharingStarted.Eagerly, isWalletBalanceHidden(walletUid))
        }

    override fun isWalletBalanceHidden(walletUid: String): Boolean =
        balanceHidden != (walletUid in _toggledWallets.value)

    override fun toggleWalletBalanceHidden(walletUid: String) {
        _toggledTransactions.update { emptySet() }
        _toggledWallets.update { current ->
            if (walletUid in current) current - walletUid else current + walletUid
        }
    }

    override fun setWalletBalanceHidden(walletUid: String, hidden: Boolean) {
        val shouldBeInSet = balanceHidden != hidden
        _toggledWallets.update { current ->
            if (shouldBeInSet) current + walletUid else current - walletUid
        }
        _toggledTransactions.update { emptySet() }
    }

    // Transaction-related functions
    override fun transactionInfoHiddenFlow(transactionId: String): StateFlow<Boolean> =
        transactionFlowCache.getOrPut(transactionId) {
            combine(
                _balanceHiddenFlow,
                _toggledTransactions
            ) { globalHidden, toggledSet ->
                globalHidden != (transactionId in toggledSet)
            }.stateIn(scope, SharingStarted.Eagerly, isTransactionInfoHidden(transactionId))
        }

    override fun transactionInfoHiddenFlowForWallet(
        transactionId: String,
        walletUid: String
    ): StateFlow<Boolean> =
        transactionForWalletFlowCache.getOrPut(transactionId to walletUid) {
            combine(
                _balanceHiddenFlow,
                _toggledWallets,
                _toggledTransactions
            ) { globalHidden, walletToggles, transactionToggles ->
                val walletHidden = globalHidden != (walletUid in walletToggles)
                walletHidden != (transactionId in transactionToggles)
            }.stateIn(scope, SharingStarted.Eagerly, isTransactionInfoHiddenForWallet(transactionId, walletUid))
        }

    override fun isTransactionInfoHidden(transactionId: String): Boolean =
        balanceHidden != (transactionId in _toggledTransactions.value)

    override fun isTransactionInfoHiddenForWallet(transactionId: String, walletUid: String): Boolean =
        isWalletBalanceHidden(walletUid) != (transactionId in _toggledTransactions.value)

    override fun toggleTransactionInfoHidden(transactionId: String) {
        _toggledTransactions.update { current ->
            if (transactionId in current) current - transactionId else current + transactionId
        }
    }

    override fun clearSessionState() {
        _toggledWallets.update { emptySet() }
        _toggledTransactions.update { emptySet() }
        walletFlowCache.clear()
        transactionFlowCache.clear()
        transactionForWalletFlowCache.clear()
    }
}
