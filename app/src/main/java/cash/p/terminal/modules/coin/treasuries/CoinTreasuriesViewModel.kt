package cash.p.terminal.modules.coin.treasuries

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.core.IAppNumberFormatter
import cash.p.terminal.core.logoUrl
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesModule.CoinTreasuriesData
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesModule.CoinTreasuryItem
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesModule.SelectorDialogState
import cash.p.terminal.modules.coin.treasuries.CoinTreasuriesModule.TreasuryTypeFilter
import cash.p.terminal.ui.compose.Select
import cash.p.terminal.wallet.models.CoinTreasury
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class CoinTreasuriesViewModel(
    private val service: CoinTreasuriesService,
    private val numberFormatter: IAppNumberFormatter
) : ViewModel() {
    val viewStateLiveData = MutableLiveData<ViewState>(ViewState.Loading)
    val isRefreshingLiveData = MutableLiveData<Boolean>()
    val coinTreasuriesLiveData = MutableLiveData<CoinTreasuriesData>()
    val treasuryTypeSelectorDialogStateLiveData = MutableLiveData<SelectorDialogState>()

    init {
        viewModelScope.launch {
            service.stateObservable.asFlow()
                .catch {
                    viewStateLiveData.postValue(ViewState.Error(it))
                }
                .collect { state ->
                    handleServiceState(state)
                }
        }

        service.start()
    }

    private fun handleServiceState(state: DataState<List<CoinTreasury>>) {
        state.dataOrNull?.let {
            viewStateLiveData.postValue(ViewState.Success)
            syncCoinTreasuriesData(it)
        }

        state.errorOrNull?.let {
            viewStateLiveData.postValue(ViewState.Error(it))
        }
    }

    fun refresh() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    fun onErrorClick() {
        refreshWithMinLoadingSpinnerPeriod()
    }

    fun onToggleSortType() {
        service.sortDescending = !service.sortDescending
    }

    fun onClickTreasuryTypeSelector() {
        treasuryTypeSelectorDialogStateLiveData.postValue(
            SelectorDialogState.Opened(Select(service.treasuryType, service.treasuryTypes))
        )
    }

    fun onSelectTreasuryType(type: TreasuryTypeFilter) {
        service.treasuryType = type
        treasuryTypeSelectorDialogStateLiveData.postValue(SelectorDialogState.Closed)
    }

    fun onTreasuryTypeSelectorDialogDismiss() {
        treasuryTypeSelectorDialogStateLiveData.postValue(SelectorDialogState.Closed)
    }

    override fun onCleared() {
        service.stop()
    }

    private fun refreshWithMinLoadingSpinnerPeriod() {
        service.refresh()
        viewModelScope.launch {
            isRefreshingLiveData.postValue(true)
            delay(1000)
            isRefreshingLiveData.postValue(false)
        }
    }

    private fun syncCoinTreasuriesData(coinTreasuries: List<CoinTreasury>) {
        val coinTreasuriesData = CoinTreasuriesData(
            Select(service.treasuryType, service.treasuryTypes),
            service.sortDescending,
            coinTreasuries.map {
                coinTreasuryItem(it)
            }
        )
        coinTreasuriesLiveData.postValue(coinTreasuriesData)
    }

    private fun coinTreasuryItem(coinTreasury: CoinTreasury) =
        CoinTreasuryItem(
            fund = coinTreasury.fund,
            fundLogoUrl = coinTreasury.logoUrl,
            country = coinTreasury.countryCode,
            amount = numberFormatter.formatCoinShort(coinTreasury.amount, service.coin.code, 8),
            amountInCurrency = numberFormatter.formatFiatShort(
                coinTreasury.amountInCurrency,
                service.currency.symbol,
                2
            )
        )
}
