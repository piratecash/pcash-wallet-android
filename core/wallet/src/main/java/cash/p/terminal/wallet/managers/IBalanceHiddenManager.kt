package cash.p.terminal.wallet.managers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IBalanceHiddenManager {
    val balanceHidden: Boolean
    val balanceAutoHidden: Boolean
    val balanceHiddenFlow: StateFlow<Boolean>
    val anyWalletVisibilityChangedFlow: Flow<Unit>
    val anyTransactionVisibilityChangedFlow: Flow<Unit>

    fun toggleBalanceHidden()
    fun setBalanceHidden(hidden: Boolean)
    fun setBalanceAutoHidden(enabled: Boolean)

    // Wallet-related functions
    fun walletBalanceHiddenFlow(walletUid: String): StateFlow<Boolean>
    fun toggleWalletBalanceHidden(walletUid: String)
    fun isWalletBalanceHidden(walletUid: String): Boolean
    fun setWalletBalanceHidden(walletUid: String, hidden: Boolean)

    // Transaction-related functions
    fun transactionInfoHiddenFlow(transactionId: String): StateFlow<Boolean>
    fun transactionInfoHiddenFlowForWallet(transactionId: String, walletUid: String): StateFlow<Boolean>
    fun toggleTransactionInfoHidden(transactionId: String)
    fun isTransactionInfoHidden(transactionId: String): Boolean
    fun isTransactionInfoHiddenForWallet(transactionId: String, walletUid: String): Boolean

    // Convenience method to check transaction visibility with optional wallet context
    fun isTransactionInfoHidden(transactionId: String, walletUid: String?): Boolean =
        walletUid?.let { isTransactionInfoHiddenForWallet(transactionId, it) }
            ?: isTransactionInfoHidden(transactionId)

    fun transactionInfoHiddenFlow(transactionId: String, walletUid: String?): StateFlow<Boolean> =
        walletUid?.let { transactionInfoHiddenFlowForWallet(transactionId, it) }
            ?: transactionInfoHiddenFlow(transactionId)

    fun clearSessionState()
}