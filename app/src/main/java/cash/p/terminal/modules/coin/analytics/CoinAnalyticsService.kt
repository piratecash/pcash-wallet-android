package cash.p.terminal.modules.coin.analytics

import cash.p.terminal.core.App
import cash.p.terminal.core.IAccountManager
import cash.p.terminal.core.InvalidAuthTokenException
import cash.p.terminal.core.NoAuthTokenException
import cash.p.terminal.core.managers.CurrencyManager
import cash.p.terminal.core.managers.MarketKitWrapper
import cash.p.terminal.core.managers.SubscriptionManager
import cash.p.terminal.entities.Currency
import cash.p.terminal.entities.DataState
import io.horizontalsystems.marketkit.models.Analytics
import io.horizontalsystems.marketkit.models.AnalyticsPreview
import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.FullCoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.rx2.await

class CoinAnalyticsService(
    val fullCoin: FullCoin,
    private val marketKit: MarketKitWrapper,
    private val currencyManager: CurrencyManager,
    private val subscriptionManager: SubscriptionManager,
    private val accountManager: IAccountManager,
) {

    private val _stateFlow = MutableStateFlow<DataState<AnalyticData>>(DataState.Loading)
    val stateFlow: Flow<DataState<AnalyticData>> = _stateFlow

    val currency: Currency
        get() = currencyManager.baseCurrency

    fun blockchain(uid: String): Blockchain? {
        return marketKit.blockchain(uid)
    }

    fun blockchains(uids: List<String>): List<Blockchain> {
        return marketKit.blockchains(uids)
    }

    suspend fun start() {
        subscriptionManager.authTokenFlow.collect {
            fetch()
        }
    }

    suspend fun refresh() {
        fetch()
    }

    private suspend fun fetch() {
        if (!subscriptionManager.hasSubscription()) {
            preview()
        } else {
            _stateFlow.emit(DataState.Loading)

            try {
                marketKit.analyticsSingle(fullCoin.coin.uid, currency.code).await()
                    .let {
                        _stateFlow.emit(DataState.Success(AnalyticData(analytics = it)))
                    }
            } catch (error: Throwable) {
                handleError(error)
            }
        }
    }

    private suspend fun handleError(error: Throwable) {
        when (error) {
            is NoAuthTokenException,
            is InvalidAuthTokenException -> {
                preview()
            }

            else -> {
                _stateFlow.emit(DataState.Error(error))
            }
        }
    }

    private suspend fun preview() {
        val addresses = accountManager.accounts.mapNotNull {
            it.type.evmAddress(App.evmBlockchainManager.getChain(BlockchainType.Ethereum))?.hex
        }

        try {
            marketKit.analyticsPreviewSingle(fullCoin.coin.uid, addresses).await()
                .let {
                    _stateFlow.emit(DataState.Success(AnalyticData(analyticsPreview = it)))
                }
        } catch (error: Throwable) {
            _stateFlow.emit(DataState.Error(error))
        }
    }

    data class AnalyticData(
        val analytics: Analytics? = null,
        val analyticsPreview: AnalyticsPreview? = null
    )

}
