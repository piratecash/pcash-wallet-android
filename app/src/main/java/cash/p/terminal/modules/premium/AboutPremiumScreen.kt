package cash.p.terminal.modules.premium

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.markdown.MarkdownContent
import cash.p.terminal.ui.compose.components.ButtonPrimaryCustomColor
import cash.p.terminal.ui_compose.components.RadialBackground
import cash.p.terminal.ui_compose.components.TitleCenteredTopBar
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.highlightText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPremiumScreen(
    uiState: AboutPremiumUiState,
    onRetryClick: () -> Unit,
    onCloseClick: () -> Unit,
    onUrlClick: (String) -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            TitleCenteredTopBar(
                title = stringResource(R.string.premium_title),
                onCloseClick = onCloseClick
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            RadialBackground()

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .padding(bottom = if (!uiState.hasPremium) 70.dp else 0.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(24.dp)
                Image(
                    painter = painterResource(id = R.drawable.prem_star_launch),
                    contentDescription = null,
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                )
                VSpacer(24.dp)
                ActionText()
                VSpacer(24.dp)


                MarkdownContent(
                    modifier = Modifier.wrapContentHeight(),
                    viewState = uiState.viewState,
                    markdownBlocks = uiState.markdownBlocks,
                    onRetryClick = onRetryClick,
                    onUrlClick = onUrlClick
                )
            }

            if (!uiState.hasPremium) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        ComposeAppTheme.colors.transparent,
                                        ComposeAppTheme.colors.tyler
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .background(ComposeAppTheme.colors.tyler)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ButtonPrimaryCustomColor(
                            modifier = Modifier.fillMaxWidth(),
                            title = stringResource(R.string.Premium_Upgrade),
                            brush = yellowGradient,
                            onClick = {

                            },
                        )
                        VSpacer(16.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionText() {
    val text = highlightText(
        text = stringResource(R.string.Premium_UpgradeText),
        textColor = ComposeAppTheme.colors.leah,
        highlightPart = stringResource(R.string.premium_title),
        highlightColor = ComposeAppTheme.colors.jacob
    )
    Text(
        text = text,
        style = ComposeAppTheme.typography.headline1,
        color = ComposeAppTheme.colors.leah,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    )
}

private val yellowGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFFD000),
        Color(0xFFFFA800),
    )
)

@Preview
@Composable
private fun SelectSubscriptionScreenPreview() {
    ComposeAppTheme {
        AboutPremiumScreen(
            uiState = AboutPremiumUiState(),
            onCloseClick = {},
            onRetryClick = {},
            onUrlClick = {}
        )
    }
}