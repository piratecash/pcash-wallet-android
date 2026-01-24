package cash.p.terminal.feature.miniapp.ui.miniapp

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_jacob
import cash.p.terminal.ui_compose.components.caption_leah
import cash.p.terminal.ui_compose.components.caption_yellow50
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun MiniAppScreen(
    uiState: MiniAppUiState,
    onConnectionClick: () -> Unit,
    onStartEarningClick: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.mini_app_title),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PremiumBonusCard(
                isConnected = uiState.isConnected,
                pirateBalanceText = uiState.pirateBalanceText,
                bonusFiatValue = uiState.bonusFiatValue,
                modifier = Modifier.padding(top = 24.dp)
            )

            GamepadIcon(
                connected = uiState.isConnected,
                modifier = Modifier.padding(top = 22.dp)
            )

            subhead1_grey(
                text = stringResource(
                    if (uiState.isConnected) {
                        R.string.mini_app_connected
                    } else {
                        R.string.mini_app_not_connected
                    }
                ),
                modifier = Modifier.padding(top = 32.dp)
            )

            VSpacer(32.dp)

            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                title = stringResource(R.string.mini_app_connection),
                onClick = onConnectionClick
            )

            VSpacer(16.dp)

            ButtonPrimaryDefault(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                title = stringResource(R.string.mini_app_start_earning),
                onClick = onStartEarningClick
            )
        }
    }
}

@Composable
private fun PremiumBonusCard(
    isConnected: Boolean,
    pirateBalanceText: String?,
    bonusFiatValue: String,
    modifier: Modifier
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ComposeAppTheme.colors.jacob, RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(top = 22.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                body_jacob(stringResource(R.string.mini_app_bonus_title))

                caption_leah(
                    text = stringResource(R.string.mini_app_bonus_description),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Image(
                painter = painterResource(R.drawable.ic_treasure_chest),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(74.dp)
            )
        }

        if (isConnected && pirateBalanceText != null) {
            val guaranteedText = buildAnnotatedString {
                withStyle(SpanStyle(color = ComposeAppTheme.colors.leah)) {
                    append(stringResource(R.string.mini_app_bonus_guaranteed))
                    append(" ")
                }
                withStyle(SpanStyle(color = ComposeAppTheme.colors.jacob)) {
                    append(pirateBalanceText)
                }
            }
            Text(
                text = guaranteedText,
                style = ComposeAppTheme.typography.subhead2,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (bonusFiatValue.isNotEmpty()) {
                caption_yellow50(bonusFiatValue)
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun GamepadIcon(
    connected: Boolean,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(ComposeAppTheme.colors.steel20),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_gamepad_48),
            contentDescription = null,
            tint = if (connected) ComposeAppTheme.colors.jacob else ComposeAppTheme.colors.grey,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MiniAppScreenPreview() {
    ComposeAppTheme {
        MiniAppScreen(
            uiState = MiniAppUiState(bonusFiatValue = "+$20", pirateBalanceText = "100", isConnected = true),
            onConnectionClick = {},
            onStartEarningClick = {},
            onClose = {}
        )
    }
}
