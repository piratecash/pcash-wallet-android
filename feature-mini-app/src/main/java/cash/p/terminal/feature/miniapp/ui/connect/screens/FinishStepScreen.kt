package cash.p.terminal.feature.miniapp.ui.connect.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.feature.miniapp.ui.components.MiniAppStepScaffold
import cash.p.terminal.feature.miniapp.ui.connect.FinishState
import cash.p.terminal.feature.miniapp.ui.components.StepDescriptionStyle
import cash.p.terminal.feature.miniapp.ui.components.StepIndicatorState
import cash.p.terminal.feature.miniapp.ui.components.rememberStepIndicatorState
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun FinishStepScreen(
    finishState: FinishState,
    onCloseClick: () -> Unit,
    onRetryClick: () -> Unit,
    stepIndicatorState: StepIndicatorState? = null,
    modifier: Modifier = Modifier
) {
    val (description, descriptionStyle) = when (finishState) {
        is FinishState.Loading -> "" to StepDescriptionStyle.Grey
        is FinishState.Success -> stringResource(R.string.connect_mini_app_connection_successful) to StepDescriptionStyle.Green
        is FinishState.Error -> buildString {
            append(stringResource(R.string.connect_mini_app_connection_failed))
            finishState.message?.takeIf { it.isNotBlank() }?.let { append(": $it") }
        } to StepDescriptionStyle.Red
    }

    MiniAppStepScaffold(
        stepTitle = stringResource(R.string.connect_mini_app_step_finish),
        stepDescription = description,
        descriptionStyle = descriptionStyle,
        stepIndicatorState = stepIndicatorState,
        isLoading = finishState is FinishState.Loading,
        modifier = modifier,
        bottomContent = {
            when (finishState) {
                is FinishState.Loading -> {
                    // No button shown during loading
                }
                is FinishState.Success -> {
                    VSpacer(16.dp)
                    ButtonPrimaryYellow(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(R.string.connect_mini_app_close),
                        onClick = onCloseClick
                    )
                }
                is FinishState.Error -> {
                    VSpacer(16.dp)
                    ButtonPrimaryYellow(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(R.string.connect_mini_app_try_again),
                        onClick = onRetryClick
                    )
                }
            }
        },
        content = {}
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinishStepScreenLoadingPreview() {
    ComposeAppTheme {
        FinishStepScreen(
            finishState = FinishState.Loading,
            onCloseClick = {},
            onRetryClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinishStepScreenSuccessPreview() {
    ComposeAppTheme {
        FinishStepScreen(
            finishState = FinishState.Success,
            onCloseClick = {},
            onRetryClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinishStepScreenErrorPreview() {
    ComposeAppTheme {
        FinishStepScreen(
            finishState = FinishState.Error("Connection failed"),
            onCloseClick = {},
            onRetryClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5)
        )
    }
}
