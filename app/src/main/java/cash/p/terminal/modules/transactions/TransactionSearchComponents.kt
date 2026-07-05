package cash.p.terminal.modules.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun TransactionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.Market_Search_Hint),
                    style = ComposeAppTheme.typography.body,
                    color = ComposeAppTheme.colors.grey50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = ComposeAppTheme.typography.body.copy(
                    color = ComposeAppTheme.colors.leah,
                ),
                maxLines = 1,
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                    }
                ),
                cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        if (query.isNotEmpty()) {
            SearchCloseButton(onClick = { onQueryChange("") })
        }
    }
}

@Composable
private fun SearchCloseButton(onClick: () -> Unit) {
    HsIconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_close_24),
            contentDescription = stringResource(R.string.Button_Cancel),
            tint = ComposeAppTheme.colors.jacob,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun SearchEmptyResultsView() {
    ListEmptyView(
        text = stringResource(R.string.transactions_empty_search_results),
        icon = R.drawable.ic_not_found
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchEmptyResultsPreview() {
    ComposeAppTheme {
        SearchEmptyResultsView()
    }
}

@Composable
internal fun SearchInProgressView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HSCircularProgressIndicator()
        VSpacer(16.dp)
        subhead2_grey(
            text = stringResource(R.string.Balance_SearchingTransactions),
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchInProgressPreview() {
    ComposeAppTheme {
        SearchInProgressView()
    }
}
