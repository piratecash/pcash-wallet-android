package cash.p.terminal.modules.offline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.core.usecase.TransitionResult
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.balance.BalanceViewHelper
import cash.p.terminal.wallet.entities.Coin
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class OfflineModeAssetItem(
    val coin: Coin,
    val badge: String?,
    val balance: String,
)

data class OfflineModeToggleUiState(
    val offline: Boolean = false,
    val description: String = "",
    val members: List<OfflineModeAssetItem> = emptyList(),
    val blockchainName: String = "",
    val isZcash: Boolean = false,
    val confirmationRequired: Boolean = false,
    val inProgress: Boolean = false,
    val error: String? = null,
    val closeSheet: Boolean = false,
)

class OfflineModeToggleViewModel(
    private val wallet: Wallet,
    private val offlineModeManager: OfflineModeManager,
    private val offlineModeUseCase: OfflineModeUseCase,
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager,
) : ViewModel() {

    private val key = OfflineKey(wallet.account.id, wallet.token.blockchainType)

    var uiState by mutableStateOf(buildInitialState())
        private set

    init {
        viewModelScope.launch {
            offlineModeManager.stateFlow.collect { rows ->
                uiState = uiState.copy(offline = rows[key]?.offline == true)
            }
        }
    }

    fun confirmOffline() {
        viewModelScope.launch { applyTransition(offline = true) }
    }

    fun goOnline() {
        viewModelScope.launch { applyTransition(offline = false) }
    }

    fun errorShown() {
        uiState = uiState.copy(error = null)
    }

    fun sheetClosed() {
        uiState = uiState.copy(closeSheet = false)
    }

    private suspend fun applyTransition(offline: Boolean) {
        if (uiState.inProgress) return
        // Pausing adapters takes time, so move the switch right away and revert it if the transition fails.
        uiState = uiState.copy(inProgress = true, offline = offline)
        val result = try {
            offlineModeUseCase.setChainOffline(wallet.account, wallet.token.blockchainType, offline)
        } finally {
            uiState = uiState.copy(inProgress = false)
        }
        uiState = when (result) {
            is TransitionResult.Success -> uiState.copy(closeSheet = true)
            is TransitionResult.Failed -> uiState.copy(
                error = Translator.getString(
                    if (offline) R.string.offline_mode_go_offline_failed
                    else R.string.offline_mode_go_online_failed
                ),
                offline = persistedOffline(),
            )

            is TransitionResult.Degraded -> uiState.copy(
                error = Translator.getString(R.string.offline_mode_state_degraded),
                offline = persistedOffline(),
            )
        }
    }

    private fun persistedOffline() = offlineModeManager.stateFlow.value[key]?.offline == true

    private fun buildInitialState(): OfflineModeToggleUiState {
        val members = buildMembers()
        return OfflineModeToggleUiState(
            offline = persistedOffline(),
            description = buildDescription(members),
            members = members,
            blockchainName = wallet.token.blockchain.name,
            isZcash = wallet.token.blockchainType == BlockchainType.Zcash,
            // A single asset means the switch affects nothing beyond itself — nothing to warn about.
            confirmationRequired = members.size > 1,
        )
    }

    private fun buildMembers(): List<OfflineModeAssetItem> =
        walletManager.activeWallets
            .filter { it.account == wallet.account && it.token.blockchainType == wallet.token.blockchainType }
            .map { member ->
                val balance = adapterManager.getBalanceAdapterForWallet(member)?.balanceData?.total
                    ?: BigDecimal.ZERO
                OfflineModeAssetItem(
                    coin = member.coin,
                    badge = member.badge,
                    balance = BalanceViewHelper.coinValue(
                        balance = balance,
                        visible = true,
                        fullFormat = true,
                        coinDecimals = member.decimal,
                        dimmed = false,
                    ).value,
                )
            }

    private fun buildDescription(members: List<OfflineModeAssetItem>): String {
        val tickers = members.map { it.coin.code }
        val assetsArg = if (tickers.size <= 2) {
            tickers.joinToString(", ")
        } else {
            val rest = members.drop(2)
            val moreArg = rest.map { it.badge }.distinct().singleOrNull() ?: rest.size.toString()
            val more = Translator.getString(R.string.offline_mode_settings_assets_more, moreArg)
            "${tickers.take(2).joinToString(", ")}, $more"
        }
        return Translator.getString(R.string.offline_mode_settings_description, wallet.token.blockchain.name, assetsArg)
    }
}
