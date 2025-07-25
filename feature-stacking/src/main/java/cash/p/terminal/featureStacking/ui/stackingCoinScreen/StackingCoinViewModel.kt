package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.featureStacking.R
import cash.p.terminal.featureStacking.ui.entities.PayoutViewItem
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.network.pirate.domain.enity.PeriodType
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.BuildConfig
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.balance.BalanceService
import cash.p.terminal.wallet.balance.BalanceViewHelper
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.smartFormat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors

internal abstract class StackingCoinViewModel(
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager,
    private val piratePlaceRepository: PiratePlaceRepository,
    private val accountManager: IAccountManager,
    private val marketKitWrapper: MarketKitWrapper,
    private val balanceService: BalanceService,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val backgroundManager: BackgroundManager
) : ViewModel() {

    abstract val minStackingAmount: Int
    abstract val stackingType: StackingType

    private val getHardwarePublicKeyForWalletUseCase: GetHardwarePublicKeyForWalletUseCase by inject<GetHardwarePublicKeyForWalletUseCase>(
        GetHardwarePublicKeyForWalletUseCase::class.java
    )

    private val customDispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
    private val _uiState = mutableStateOf(
        StackingCoinUIState(
            balanceHidden = balanceHiddenManager.balanceHiddenFlow.value
        )
    )
    val uiState: State<StackingCoinUIState> get() = _uiState
    private var wallet: Wallet? = null

    init {
        viewModelScope.launch {
            balanceHiddenManager.balanceHiddenFlow.collectLatest {
                _uiState.value = uiState.value.copy(balanceHidden = it)
            }
        }
    }

    fun toggleBalanceVisibility() {
        balanceHiddenManager.toggleBalanceHidden()
    }

    fun loadData() = viewModelScope.launch {
        createWalletIfNotExist()
        loadAnnualInterest()
        viewModelScope.launch(customDispatcher) {
            balanceService.balanceItemsFlow.collect { items ->
                if (items?.find {
                        it.state == AdapterState.Synced && it.wallet.token.type is TokenType.Eip20 &&
                                (it.wallet.token.type as TokenType.Eip20).address.equals(
                                    getContract(),
                                    true
                                )
                    } != null) {
                    loadBalance()
                    cancel()
                }
            }
        }
        balanceService.start()
    }

    fun refresh() {
        _uiState.value = uiState.value.copy(
            isRefreshing = true
        )
        viewModelScope.launch {
            loadAnnualInterest()
            loadBalance()
            balanceService.refresh()
            delay(1000)
            _uiState.value = uiState.value.copy(
                isRefreshing = false
            )
        }
    }

    private fun loadAnnualInterest() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val data = piratePlaceRepository.getCalculatorData(
                coin = stackingType.value,
                amount = 100.0
            )
            data.items.find { it.periodType == PeriodType.YEAR }?.let {
                _uiState.value = uiState.value.copy(
                    annualInterest = it.amount.smartFormat() + "%"
                )
            }
        } catch (e: Exception) {
            Log.e("StackingCoinViewModel", "Error loading annual interest", e)
        }
    }

    private suspend fun createWalletIfNotExist() {
        val contract = getContract()
        wallet = getActiveContractWallet(contract)
        if (wallet == null) {
            val account = accountManager.activeAccount ?: return
            val tokenQuery = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Eip20(contract))
            marketKitWrapper.token(tokenQuery)?.let { token ->
                wallet = Wallet(
                    token = token,
                    account = account,
                    hardwarePublicKey = getHardwarePublicKeyForWalletUseCase(
                        account = account, blockchainType = token.blockchainType,
                        tokenType = token.type
                    )
                ).also {
                    walletManager.save(listOf(it))
                }
            }
        }
    }

    private fun getActiveContractWallet(token: String) = walletManager.activeWallets.find {
        it.token.type is TokenType.Eip20 &&
                (it.token.type as TokenType.Eip20).address.equals(token, true)
    }

    private fun getContract(): String =
        if (stackingType == StackingType.PCASH) {
            BuildConfig.PIRATE_CONTRACT
        } else {
            BuildConfig.COSANTA_CONTRACT
        }

    private fun loadBalance() {
        val wallet = walletManager.activeWallets.find {
            it.token.type is TokenType.Eip20 && (it.token.type as TokenType.Eip20).address.equals(
                getContract(),
                true
            )
        }
        val receiveAddress: String = wallet?.let {
            adapterManager.getReceiveAdapterForWallet(wallet)?.receiveAddress ?: ""
        } ?: ""
        val balance: BigDecimal = wallet?.let {
            adapterManager.getBalanceAdapterForWallet(wallet)?.balanceData?.total
        } ?: BigDecimal.ZERO
        _uiState.value = uiState.value.copy(
            stackingType = stackingType,
            minStackingAmount = minStackingAmount.toBigDecimal(),
            balance = balance,
            balanceStr = BalanceViewHelper.coinValue(
                balance = balance,
                visible = true,
                fullFormat = true,
                coinDecimals = wallet?.decimal ?: 8,
                dimmed = false
            ).value,
            token = wallet?.token,
            receiveAddress = receiveAddress,
            isWatchAccount = wallet?.account?.isWatchAccount ?: false
        )

        viewModelScope.launch(
            customDispatcher +
                    CoroutineExceptionHandler { _, throwable ->
                        Log.e("StackingCoinViewModel", "Error loading balance", throwable)
                    }) {
            balanceService.balanceItemsFlow.collectLatest { items ->
                if (backgroundManager.stateFlow.value != BackgroundManagerState.EnterForeground) {
                    return@collectLatest
                }
                items?.find { it.wallet.coin.code == stackingType.value }?.let { item ->
                    loadInvestmentData(
                        balance = item.balanceData.total,
                        wallet = wallet,
                        coinPrice = item.coinPrice
                    )
                    uiState.value.receiveAddress?.let {
                        loadPayouts(
                            address = it,
                            coinPrice = item.coinPrice
                        )
                    }
                }
            }
        }
    }

    private fun loadInvestmentData(balance: BigDecimal, wallet: Wallet?, coinPrice: CoinPrice?) =
        viewModelScope.launch(
            customDispatcher +
                    CoroutineExceptionHandler { _, throwable ->
                        Log.e("StackingCoinViewModel", "Error loading investment data", throwable)
                        _uiState.value = uiState.value.copy(
                            loading = false
                        )
                    }) {
            var unpaid: BigDecimal = BigDecimal.ZERO
            var totalIncome: BigDecimal = BigDecimal.ZERO
            var secondaryAmount = ""
            if (wallet != null) {
                adapterManager.getReceiveAdapterForWallet(wallet)?.receiveAddress?.let { receiveAddress ->
                    val investmentData = piratePlaceRepository.getInvestmentData(
                        coin = stackingType.value.lowercase(),
                        address = receiveAddress
                    )
                    unpaid = investmentData.unrealizedValue.toBigDecimal()
                    totalIncome = investmentData.mint.toBigDecimal()
                }
                secondaryAmount = BalanceViewHelper.currencyValue(
                    balance = balance,
                    coinPrice = coinPrice,
                    visible = true,
                    fullFormat = false,
                    currency = balanceService.baseCurrency,
                    dimmed = false
                ).value
            }
            val totalIncomeSecondary = BalanceViewHelper.currencyValue(
                balance = totalIncome,
                coinPrice = coinPrice,
                visible = true,
                fullFormat = false,
                currency = balanceService.baseCurrency,
                dimmed = false
            ).value
            val unpaidSecondary = BalanceViewHelper.currencyValue(
                balance = unpaid,
                coinPrice = coinPrice,
                visible = true,
                fullFormat = false,
                currency = balanceService.baseCurrency,
                dimmed = false
            ).value
            _uiState.value = uiState.value.copy(
                unpaidStr = BalanceViewHelper.coinValue(
                    balance = unpaid,
                    visible = true,
                    fullFormat = true,
                    coinDecimals = wallet?.decimal ?: 8,
                    dimmed = false
                ).value,
                secondaryAmount = secondaryAmount,
                totalIncomeStr = BalanceViewHelper.coinValue(
                    balance = totalIncome,
                    visible = true,
                    fullFormat = true,
                    coinDecimals = wallet?.decimal ?: 8,
                    dimmed = false
                ).value,
                totalIncomeSecondary = totalIncomeSecondary,
                unpaidSecondary = unpaidSecondary,
                loading = false
            )
        }

    private suspend fun loadPayouts(address: String, coinPrice: CoinPrice?) {
        val payouts = piratePlaceRepository.getStakeData(
            coin = stackingType.value,
            address = address
        ).stakes.map {
            val date = Date(it.createdAt)
            PayoutViewItem(
                id = it.id,
                date = formatDate(date).uppercase(),
                time = DateHelper.getOnlyTimeWithSec(date),
                payoutType = it.type,
                amount = it.amount.toBigDecimal(),
                amountSecondary = BalanceViewHelper.currencyValue(
                    balance = it.amount.toBigDecimal(),
                    coinPrice = coinPrice,
                    visible = true,
                    fullFormat = false,
                    currency = balanceService.baseCurrency,
                    dimmed = false
                ).value
            )
        }.groupBy { it.date }
        _uiState.value = uiState.value.copy(
            payoutItems = payouts
        )
    }

    private fun formatDate(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date

        val today = Calendar.getInstance()
        if (calendar[Calendar.YEAR] == today[Calendar.YEAR] && calendar[Calendar.DAY_OF_YEAR] == today[Calendar.DAY_OF_YEAR]) {
            return Translator.getString(R.string.Timestamp_Today)
        }

        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_MONTH, -1)
        if (calendar[Calendar.YEAR] == yesterday[Calendar.YEAR] && calendar[Calendar.DAY_OF_YEAR] == yesterday[Calendar.DAY_OF_YEAR]) {
            return Translator.getString(R.string.Timestamp_Yesterday)
        }

        return DateHelper.shortDate(date, "MMMM d", "MMMM d, yyyy")
    }

    override fun onCleared() {
        balanceService.clear()
        customDispatcher.close()
    }
}