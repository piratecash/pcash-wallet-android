package cash.p.terminal.modules.paycore.selectbank

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.paycore.PAYCORE_COMPLETE_BACK_URL
import cash.p.terminal.modules.paycore.webview.PayCoreWebViewScreen
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionPremiumUniversalLawrence
import cash.p.terminal.ui_compose.components.body_grey

@Composable
fun PayCoreSelectBankHost(
    uiState: PayCoreSelectBankUiState,
    onCloseWebView: () -> Unit,
    onClearError: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val webViewTitle = stringResource(R.string.paycore_select_bank)
    val currentOnClearError by rememberUpdatedState(onClearError)

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            HudHelper.showErrorMessage(view, it)
            currentOnClearError()
        }
    }

    val activeUrl = uiState.webViewUrl
    if (activeUrl != null) {
        PayCoreWebViewScreen(
            url = activeUrl,
            title = webViewTitle,
            onClose = onCloseWebView,
            onInterceptBackUrl = onCloseWebView,
            backUrlPrefix = PAYCORE_COMPLETE_BACK_URL,
        )
    } else {
        content()
    }
}

@Composable
fun PayCoreSelectBankActionSection(
    onClick: () -> Unit,
    showSpinner: Boolean,
    modifier: Modifier = Modifier,
) {
    SectionPremiumUniversalLawrence(modifier = modifier) {
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = if (showSpinner) null else onClick,
        ) {
            body_grey(
                text = stringResource(R.string.paycore_select_bank),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showSpinner) {
                HSCircularProgressIndicator(progress = 0.15f, size = 20.dp)
            } else {
                Image(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                )
            }
        }
    }
}
