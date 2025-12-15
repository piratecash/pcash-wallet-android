package cash.p.terminal.modules.settings.appcache

import androidx.annotation.StringRes
import cash.p.terminal.R

object AppCacheModule {

    data class UiState(
        val cacheItems: List<CacheItemViewItem> = emptyList(),
        val isClearing: Boolean = false,
        val clearingItemId: CacheType? = null
    )

    data class CacheItemViewItem(
        val type: CacheType,
        @StringRes val titleResId: Int,
        @StringRes val descriptionResId: Int
    )

    enum class CacheType(
        @StringRes val titleResId: Int,
        @StringRes val descriptionResId: Int
    ) {
        HTTP(
            R.string.settings_app_cache_http_title,
            R.string.settings_app_cache_http_description
        ),
        IMAGE(
            R.string.settings_app_cache_image_title,
            R.string.settings_app_cache_image_description
        ),
        DATABASE(
            R.string.settings_app_cache_database_title,
            R.string.settings_app_cache_database_description
        ),
        MARKET_PRICES(
            R.string.settings_app_cache_market_title,
            R.string.settings_app_cache_market_description
        )
    }
}
