package cash.p.terminal.feature.miniapp.ui.connect.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.feature.miniapp.ui.components.MiniAppStepScaffold
import cash.p.terminal.feature.miniapp.ui.components.StepDescriptionStyle
import cash.p.terminal.feature.miniapp.ui.components.StepIndicatorState
import cash.p.terminal.feature.miniapp.ui.components.rememberStepIndicatorState
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.R as ResourcesR

@Composable
fun TokenMissingScreen(
    allTokensText: String,
    missingTokenNames: List<String>,
    isAddingTokens: Boolean,
    onAddClick: () -> Unit,
    stepIndicatorState: StepIndicatorState,
    modifier: Modifier = Modifier
) {
    val missingTokensText = missingTokenNames.joinToString(", ")

    MiniAppStepScaffold(
        stepTitle = stringResource(R.string.connect_mini_app_step_1),
        stepDescription = stringResource(R.string.connect_mini_app_checking_tokens, allTokensText),
        descriptionStyle = StepDescriptionStyle.Grey,
        stepIndicatorState = stepIndicatorState,
        isLoading = isAddingTokens,
        scrollable = false,
        modifier = modifier,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(ComposeAppTheme.colors.steel10),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(ResourcesR.drawable.ic_sync_error),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.grey,
                        modifier = Modifier.size(48.dp)
                    )
                }

                VSpacer(24.dp)

                subhead1_grey(
                    text = stringResource(R.string.connect_mini_app_missing_tokens, missingTokensText),
                    textAlign = TextAlign.Center
                )

                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    title = stringResource(R.string.connect_mini_app_add_tokens),
                    onClick = onAddClick,
                    enabled = !isAddingTokens
                )
                Spacer(Modifier.weight(3f))
            }
        }
    )
}

@Composable
fun TokenCheckingScreen(
    stepIndicatorState: StepIndicatorState? = null,
    modifier: Modifier = Modifier
) {
    MiniAppStepScaffold(
        stepTitle = stringResource(R.string.connect_mini_app_step_1),
        stepDescription = stringResource(
            R.string.connect_mini_app_checking_tokens,
            "Pirate JETTON, Cosanta BNB, Pirate BNB"
        ),
        descriptionStyle = StepDescriptionStyle.Grey,
        stepIndicatorState = stepIndicatorState,
        isLoading = true,
        modifier = modifier,
        bottomContent = {},
        content = {}
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TokenMissingScreenPreview() {
    ComposeAppTheme {
        TokenMissingScreen(
            allTokensText = "Pirate JETTON, PirateCash BEP20, Cosanta BEP20",
            missingTokenNames = listOf("Pirate JETTON", "Cosanta BEP20"),
            isAddingTokens = false,
            onAddClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 1)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TokenCheckingScreenPreview() {
    ComposeAppTheme {
        TokenCheckingScreen(
            stepIndicatorState = rememberStepIndicatorState(initialStep = 1)
        )
    }
}
