package cash.p.terminal.modules.zcashmigration

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.zcash.SendZCashViewModel
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.z.ecc.android.sdk.ext.collectWith
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal

class ZcashMigrationViewModel(
    private val wallet: Wallet,
    private val locallyCreatedTransactionRepository: LocallyCreatedTransactionRepository,
    private val numberFormatter: IAppNumberFormatter,
    private val adapterManager: IAdapterManager,
    xRateService: XRateService,
) : ViewModelUiState<ZcashMigrationUiState>() {

    private val logger = AppLogger("zcash-ironwood-migration")

    /**
     * Resolved on every use: adapters are stopped and replaced when the active account changes,
     * so a cached one would keep signing against a closed Synchronizer.
     */
    private val adapter: ZcashAdapter?
        get() = adapterManager.getAdapterForWallet(wallet)

    private var coinRate: CurrencyValue? = xRateService.getRate(wallet.coin.uid)

    /** Full Orchard balance until the proposal replaces it with the exact amount net of fee. */
    private var amount: BigDecimal = adapter?.ironwoodMigrationRequiredBalance ?: BigDecimal.ZERO
    private var fee: BigDecimal? = null
    private var sendResult: SendResult? = null
    private var error: HSCaution? = null
    private var prepareJob: Job? = null

    init {
        xRateService.getRateFlow(wallet.coin.uid).collectWith(viewModelScope) {
            coinRate = it
            emitState()
        }
    }

    /**
     * Requests a fresh proposal, dropping whatever a previous visit to the screen has left. This
     * is also the only way to repeat a failed migration, since the adapter consumes its proposal
     * on the first attempt.
     *
     * A send that is running or has succeeded is never reset: the screen is recomposed from
     * scratch after an app lock, and its terminal result still has to reach the user.
     */
    fun prepare() {
        if (sendResult == SendResult.Sending || sendResult is SendResult.Sent) return

        amount = adapter?.ironwoodMigrationRequiredBalance ?: BigDecimal.ZERO
        fee = null
        sendResult = null
        error = null
        emitState()

        // An earlier request would otherwise finish later and show a fee that belongs to a
        // proposal the adapter has already replaced.
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            try {
                val proposal = requireAdapter().proposeIronwoodMigration()
                amount = proposal.amount
                fee = proposal.fee
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warning("propose failed", e)
                error = SendZCashViewModel.createCaution(e)
            }
            emitState()
        }
    }

    override fun createState() = ZcashMigrationUiState(
        amount = formatCoin(amount),
        amountFiat = formatFiat(amount),
        fee = fee?.let(::formatCoin),
        feeFiat = fee?.let(::formatFiat),
        migrateEnabled = canMigrate(),
        sendResult = sendResult,
        error = error,
    )

    /**
     * Only an untouched attempt may be sent: the amount and fee on screen belong to a proposal
     * the adapter consumes on the first tap, so repeating goes through [prepare] instead.
     */
    private fun canMigrate() = fee != null && error == null && sendResult == null

    fun onClickMigrate() {
        if (sendResult != null) return

        val scopedLogger = logger.getScopedUnique()
        viewModelScope.launch {
            sendResult = SendResult.Sending
            emitState()
            try {
                val transactionHash = requireAdapter().executeIronwoodMigration()
                locallyCreatedTransactionRepository.markCreated(wallet, transactionHash)
                scopedLogger.info("success")
                sendResult = SendResult.Sent(transactionHash)
            } catch (e: Throwable) {
                scopedLogger.warning("failed", e)
                sendResult = SendResult.Failed(SendZCashViewModel.createCaution(e))
            }
            emitState()
        }
    }

    private fun requireAdapter() = checkNotNull(adapter) {
        "ZcashAdapter is not available for ${wallet.coin.code}"
    }

    private fun formatCoin(value: BigDecimal) =
        numberFormatter.formatCoinFull(value, wallet.coin.code, wallet.decimal)

    private fun formatFiat(value: BigDecimal) =
        coinRate?.let { it.copy(value = value * it.value).getFormattedFull() }
}

data class ZcashMigrationUiState(
    val amount: String,
    val amountFiat: String?,
    val fee: String?,
    val feeFiat: String?,
    val migrateEnabled: Boolean,
    val sendResult: SendResult?,
    val error: HSCaution?,
)
