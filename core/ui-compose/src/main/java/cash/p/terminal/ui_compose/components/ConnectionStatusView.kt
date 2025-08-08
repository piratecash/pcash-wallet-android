package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.getKoinInstance
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun ConnectionStatusView(
    connectionManager: IConnectivityManager = getKoinInstance(),
    modifier: Modifier = Modifier
) {
    val isConnected by connectionManager.isConnected.collectAsState(
        initial = true
    )
    if (connectionManager.torEnabled) {
        TorStatusView(modifier = modifier)
    } else if (!isConnected) {
        NoConnectionView(modifier = modifier)
    }
}

@Composable
private fun NoConnectionView(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.Hud_Text_NoInternet),
        style = ComposeAppTheme.typography.micro,
        textAlign = TextAlign.Center,
        color = ComposeAppTheme.colors.lawrence,
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(colorResource(R.color.red_d))
            .wrapContentHeight(Alignment.CenterVertically)
    )
}

@Preview(showBackground = true)
@Composable
private fun ConnectionStatusViewPreview() {
    ComposeAppTheme {
        NoConnectionView()
    }
}
