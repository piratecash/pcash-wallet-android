package cash.p.terminal.feature.miniapp.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.ui_compose.components.ButtonPrimaryLight
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun LinkExpiredContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(ComposeAppTheme.colors.raina, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(R.drawable.ic_not_found_48),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey
            )
        }
        Spacer(Modifier.height(32.dp))
        subhead2_grey(
            text = stringResource(R.string.connect_mini_app_link_expired),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

/**
 * Reusable composable for JWT expired state across all mini app steps.
 */
@Composable
fun JwtExpiredStepContent(
    stepTitle: String,
    stepIndicatorState: StepIndicatorState?,
    onOpenMiniAppClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MiniAppStepScaffold(
        stepTitle = stepTitle,
        stepDescription = "",
        descriptionStyle = StepDescriptionStyle.Grey,
        stepIndicatorState = stepIndicatorState,
        modifier = modifier,
        bottomContent = {
            ButtonPrimaryLight(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.connect_mini_app_open_mini_app),
                onClick = onOpenMiniAppClick
            )
        },
        content = {
            Spacer(Modifier.weight(1f))
            LinkExpiredContent()
            Spacer(Modifier.weight(1f))
        }
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinkExpiredContentPreview() {
    ComposeAppTheme {
        LinkExpiredContent()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JwtExpiredStepContentPreview() {
    ComposeAppTheme {
        JwtExpiredStepContent(
            stepTitle = "Step 4",
            stepIndicatorState = rememberStepIndicatorState(initialStep = 4),
            onOpenMiniAppClick = {}
        )
    }
}
