package cash.p.terminal.feature.miniapp.ui.connect.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.feature.miniapp.ui.components.MiniAppStepScaffold
import cash.p.terminal.feature.miniapp.ui.components.StepDescriptionStyle
import cash.p.terminal.feature.miniapp.ui.components.StepIndicatorState
import cash.p.terminal.feature.miniapp.ui.components.rememberStepIndicatorState
import cash.p.terminal.ui_compose.annotatedStringResource
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun AcceptTermsStepScreen(
    isAgreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onContinueClick: () -> Unit,
    stepIndicatorState: StepIndicatorState? = null,
    modifier: Modifier = Modifier
) {
    MiniAppStepScaffold(
        stepTitle = stringResource(R.string.connect_mini_app_step_2),
        stepDescription = stringResource(R.string.connect_mini_app_step_2_description),
        descriptionStyle = StepDescriptionStyle.Grey,
        stepIndicatorState = stepIndicatorState,
        modifier = modifier,
        bottomContent = {
            VSpacer(16.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.Button_Continue),
                onClick = onContinueClick,
                enabled = isAgreed
            )
        },
        content = {
            VSpacer(24.dp)

            TermsInfoBox(Modifier.padding(horizontal = 16.dp))

            VSpacer(24.dp)

            AgreementCheckbox(
                isAgreed = isAgreed,
                onAgreedChange = onAgreedChange,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            VSpacer(24.dp)
        }
    )
}

@Composable
private fun TermsInfoBox(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ComposeAppTheme.colors.jacob, RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.yellow20)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            text = stringResource(R.string.connect_mini_app_terms_title),
            style = ComposeAppTheme.typography.subhead1,
            color = ComposeAppTheme.colors.leah
        )

        // Body text with bold formatting
        AnnotatedText(annotatedStringResource(R.string.connect_mini_app_terms_body))

        // Bullet points for data types
        BulletPoint(stringResource(R.string.connect_mini_app_terms_bullet_1))
        BulletPoint(stringResource(R.string.connect_mini_app_terms_bullet_2))
        BulletPoint(stringResource(R.string.connect_mini_app_terms_bullet_3))

        VSpacer(4.dp)

        // Fingerprint explanation
        AnnotatedText(annotatedStringResource(R.string.connect_mini_app_terms_fingerprint))

        VSpacer(4.dp)

        // Important section with pushpin emoji
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\uD83D\uDCCC ",
                style = ComposeAppTheme.typography.subhead1
            )
            Text(
                text = stringResource(R.string.connect_mini_app_terms_important_title),
                style = ComposeAppTheme.typography.subhead1,
                color = ComposeAppTheme.colors.leah
            )
        }

        // Important bullet points
        AnnotatedBulletPoint(annotatedStringResource(R.string.connect_mini_app_terms_important_1))
        AnnotatedBulletPoint(annotatedStringResource(R.string.connect_mini_app_terms_important_2))
        AnnotatedBulletPoint(annotatedStringResource(R.string.connect_mini_app_terms_important_3))
        AnnotatedBulletPoint(annotatedStringResource(R.string.connect_mini_app_terms_important_4))
        BulletPoint(stringResource(R.string.connect_mini_app_terms_important_5))
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        subhead2_leah(text = "\u2022")
        subhead2_leah(text = text)
    }
}

@Composable
private fun AnnotatedBulletPoint(text: AnnotatedString) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        subhead2_leah(text = "\u2022")
        AnnotatedText(text)
    }
}

@Composable
private fun AnnotatedText(text: AnnotatedString) {
    Text(
        text = text,
        style = ComposeAppTheme.typography.subhead2,
        color = ComposeAppTheme.colors.leah
    )
}

@Composable
private fun AgreementCheckbox(
    isAgreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .clickable { onAgreedChange(!isAgreed) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HsCheckbox(
            checked = isAgreed,
            onCheckedChange = onAgreedChange
        )
        subhead2_leah(
            text = stringResource(R.string.connect_mini_app_terms_agreement),
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AcceptTermsStepScreenPreview() {
    ComposeAppTheme {
        AcceptTermsStepScreen(
            isAgreed = false,
            onAgreedChange = {},
            onContinueClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 2)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AcceptTermsStepScreenWithBackPreview() {
    ComposeAppTheme {
        AcceptTermsStepScreen(
            isAgreed = false,
            onAgreedChange = {},
            onContinueClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 2)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AcceptTermsStepScreenAgreedPreview() {
    ComposeAppTheme {
        AcceptTermsStepScreen(
            isAgreed = true,
            onAgreedChange = {},
            onContinueClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 2)
        )
    }
}
