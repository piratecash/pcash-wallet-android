package cash.p.terminal.feature.miniapp.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_jacob
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.components.subhead2_remus
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

enum class StepDescriptionStyle {
    Grey,   // Normal steps
    Red,    // Error/failure
    Green,  // Success
    Yellow  // Premium/special
}

@Composable
fun MiniAppStepScaffold(
    stepTitle: String?,
    stepDescription: String,
    descriptionStyle: StepDescriptionStyle = StepDescriptionStyle.Grey,
    stepIndicatorState: StepIndicatorState? = null,
    isLoading: Boolean = false,
    scrollable: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // FIXED HEADER
        VSpacer(24.dp)

        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                if (stepTitle != null) {
                    headline1_leah(
                        text = stepTitle
                    )

                    VSpacer(8.dp)
                } else {
                    VSpacer(24.dp)
                }

                Box(modifier = Modifier.heightIn(min = 40.dp)) {
                    when (descriptionStyle) {
                        StepDescriptionStyle.Grey -> subhead2_grey(text = stepDescription)
                        StepDescriptionStyle.Red -> subhead2_lucian(text = stepDescription)
                        StepDescriptionStyle.Green -> subhead2_remus(text = stepDescription)
                        StepDescriptionStyle.Yellow -> subhead2_jacob(text = stepDescription)
                    }
                }

                if (stepIndicatorState != null) {
                    VSpacer(16.dp)

                    StepIndicator(
                        state = stepIndicatorState,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Image(
                painter = painterResource(R.drawable.img_pirate_treasure),
                contentDescription = null,
                modifier = Modifier.size(117.dp)
            )
        }

        // SCROLLABLE CONTENT or LOADING
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ComposeAppTheme.colors.jacob)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (scrollable) Modifier.verticalScroll(scrollState).imePadding() else Modifier)
            ) {
                content()
            }

            if (bottomContent != null) {
                Column(
                    modifier = Modifier.offset(y = -(24.dp))
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
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        bottomContent()
                    }
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MiniAppStepScaffoldNormalPreview() {
    ComposeAppTheme {
        MiniAppStepScaffold(
            isLoading = true,
            stepTitle = "Step 1",
            stepDescription = "To connect, you need to create or import a wallet.",
            descriptionStyle = StepDescriptionStyle.Grey,
            stepIndicatorState = rememberStepIndicatorState(initialStep = 1),
            content = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MiniAppStepScaffoldErrorPreview() {
    ComposeAppTheme {
        MiniAppStepScaffold(
            stepTitle = null,
            stepDescription = "Registration is only possible on real devices, emulators cannot be used for this task.",
            descriptionStyle = StepDescriptionStyle.Red,
            stepIndicatorState = null,
            content = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MiniAppStepScaffoldSuccessPreview() {
    ComposeAppTheme {
        MiniAppStepScaffold(
            stepTitle = "Finish",
            stepDescription = "The connection was successful.",
            descriptionStyle = StepDescriptionStyle.Green,
            stepIndicatorState = null,
            content = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MiniAppStepScaffoldPremiumPreview() {
    ComposeAppTheme {
        MiniAppStepScaffold(
            stepTitle = "Step 5",
            stepDescription = "Special proposal for premium members",
            descriptionStyle = StepDescriptionStyle.Yellow,
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5),
            content = {}
        )
    }
}
