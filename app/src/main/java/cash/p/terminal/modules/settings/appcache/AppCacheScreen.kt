package cash.p.terminal.modules.settings.appcache

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheItemViewItem
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheType
import cash.p.terminal.modules.settings.appcache.AppCacheModule.UiState
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppCacheScreen(navController: NavController) {
    val viewModel = koinViewModel<AppCacheViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val localView = LocalView.current

    AppCacheContent(
        uiState = uiState,
        onBackClick = { navController.popBackStack() },
        onClearAllClick = {
            viewModel.clearAllCaches()
            HudHelper.showSuccessMessage(localView, R.string.settings_app_cache_cleared)
        },
        onClearItemClick = { type ->
            viewModel.clearCache(type)
            HudHelper.showSuccessMessage(localView, R.string.settings_app_cache_cleared)
        }
    )
}

@Composable
private fun AppCacheContent(
    uiState: UiState,
    onBackClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onClearItemClick: (CacheType) -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.settings_app_cache_title),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(12.dp)

            ButtonPrimaryRed(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.settings_app_cache_clear_all),
                onClick = onClearAllClick,
                enabled = !uiState.isClearing
            )

            VSpacer(24.dp)

            CellUniversalLawrenceSection(uiState.cacheItems) { item ->
                CacheItemRow(
                    item = item,
                    isClearing = uiState.clearingItemId == item.type,
                    onClearClick = { onClearItemClick(item.type) }
                )
            }

            VSpacer(12.dp)

            InfoText(text = stringResource(R.string.settings_app_cache_description))

            VSpacer(32.dp)
        }
    }
}

@Composable
private fun CacheItemRow(
    item: CacheItemViewItem,
    isClearing: Boolean,
    onClearClick: () -> Unit
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = stringResource(item.titleResId))
            VSpacer(1.dp)
            subhead2_grey(text = stringResource(item.descriptionResId))
        }

        HSpacer(16.dp)

        if (isClearing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ComposeAppTheme.colors.grey,
                strokeWidth = 2.dp
            )
        } else {
            ButtonPrimaryYellow(
                title = stringResource(R.string.settings_app_cache_clear),
                onClick = onClearClick
            )
        }
    }
}

@Preview
@Composable
private fun AppCacheScreenPreview() {
    val previewState = UiState(
        cacheItems = listOf(
            CacheItemViewItem(
                CacheType.HTTP,
                R.string.settings_app_cache_http_title,
                R.string.settings_app_cache_http_description
            ),
            CacheItemViewItem(
                CacheType.IMAGE,
                R.string.settings_app_cache_image_title,
                R.string.settings_app_cache_image_description
            ),
            CacheItemViewItem(
                CacheType.DATABASE,
                R.string.settings_app_cache_database_title,
                R.string.settings_app_cache_database_description
            ),
            CacheItemViewItem(
                CacheType.MARKET_PRICES,
                R.string.settings_app_cache_market_title,
                R.string.settings_app_cache_market_description
            )
        )
    )

    ComposeAppTheme {
        AppCacheContent(
            uiState = previewState,
            onBackClick = {},
            onClearAllClick = {},
            onClearItemClick = {}
        )
    }
}
