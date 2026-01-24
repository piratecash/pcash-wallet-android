package cash.p.terminal.feature.miniapp.ui.connect.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.ui.components.JwtExpiredStepContent
import cash.p.terminal.feature.miniapp.ui.components.MiniAppStepScaffold
import cash.p.terminal.feature.miniapp.ui.connect.FinishState
import cash.p.terminal.feature.miniapp.ui.components.StepDescriptionStyle
import cash.p.terminal.feature.miniapp.ui.components.StepIndicatorState
import cash.p.terminal.feature.miniapp.ui.components.rememberStepIndicatorState
import cash.p.terminal.strings.R
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun FinishStepScreen(
    finishState: FinishState,
    onCloseClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenMiniAppClick: () -> Unit,
    stepIndicatorState: StepIndicatorState? = null,
    modifier: Modifier = Modifier
) {
    // Handle JWT expired state with dedicated UI
    if (finishState is FinishState.JwtExpired) {
        JwtExpiredStepContent(
            stepTitle = stringResource(R.string.connect_mini_app_step_finish),
            stepIndicatorState = stepIndicatorState,
            onOpenMiniAppClick = onOpenMiniAppClick,
            modifier = modifier
        )
        return
    }

    val (description, descriptionStyle) = when (finishState) {
        is FinishState.Loading -> "" to StepDescriptionStyle.Grey
        is FinishState.Success -> stringResource(R.string.connect_mini_app_connection_successful) to StepDescriptionStyle.Green
        is FinishState.JwtExpired -> "" to StepDescriptionStyle.Grey // Already handled above
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
                is FinishState.Loading, is FinishState.JwtExpired -> {
                    // No button shown during loading or JWT expired (handled above)
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
            onOpenMiniAppClick = {},
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
            onOpenMiniAppClick = {},
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
            onOpenMiniAppClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinishStepScreenJwtExpiredPreview() {
    ComposeAppTheme {
        FinishStepScreen(
            finishState = FinishState.JwtExpired,
            onCloseClick = {},
            onRetryClick = {},
            onOpenMiniAppClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5)
        )
    }
}
