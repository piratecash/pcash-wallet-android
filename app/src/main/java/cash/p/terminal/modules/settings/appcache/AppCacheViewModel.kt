package cash.p.terminal.modules.settings.appcache

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheItemViewItem
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheType
import cash.p.terminal.modules.settings.appcache.AppCacheModule.UiState
import cash.p.terminal.wallet.HSCache
import cash.p.terminal.wallet.storage.MarketDatabase
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppCacheViewModel(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val marketDatabase: MarketDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UiState(
            cacheItems = CacheType.entries.map { type ->
                CacheItemViewItem(
                    type = type,
                    titleResId = type.titleResId,
                    descriptionResId = type.descriptionResId
                )
            }
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun clearAllCaches() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.value = _uiState.value.copy(isClearing = true)

            clearHttpCache()
            clearImageCache()
            clearDatabaseCache()
            clearMarketPricesCache()

            delay(300)

            _uiState.value = _uiState.value.copy(isClearing = false)
        }
    }

    fun clearCache(type: CacheType) {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.value = _uiState.value.copy(clearingItemId = type)

            when (type) {
                CacheType.HTTP -> clearHttpCache()
                CacheType.IMAGE -> clearImageCache()
                CacheType.DATABASE -> clearDatabaseCache()
                CacheType.MARKET_PRICES -> clearMarketPricesCache()
            }

            delay(300)
            _uiState.value = _uiState.value.copy(clearingItemId = null)
        }
    }

    private suspend fun clearHttpCache() {
        withContext(dispatcherProvider.io) {
            runCatching {
                HSCache.cacheDir?.let { dir ->
                    if (dir.exists()) {
                        dir.listFiles()?.forEach { file ->
                            file.deleteRecursively()
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private suspend fun clearImageCache() {
        withContext(dispatcherProvider.io) {
            runCatching {
                context.imageLoader.diskCache?.clear()
                context.imageLoader.memoryCache?.clear()
            }
        }
    }

    private suspend fun clearDatabaseCache() {
        withContext(dispatcherProvider.io) {
            runCatching {
                context.deleteDatabase("db_cache")
            }
        }
    }

    private suspend fun clearMarketPricesCache() {
        withContext(dispatcherProvider.io) {
            runCatching {
                marketDatabase.coinPriceDao().deleteAll()
                marketDatabase.coinHistoricalPriceDao().deleteAll()
            }
        }
    }
}
