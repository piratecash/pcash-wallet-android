package cash.p.terminal.feature.miniapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Stable
class StepIndicatorState(
    initialStep: Int = 1,
    val totalSteps: Int = 5
) {
    var currentStep by mutableIntStateOf(initialStep)
}

@Composable
fun rememberStepIndicatorState(
    initialStep: Int = 1,
    totalSteps: Int = 5
): StepIndicatorState {
    return remember { StepIndicatorState(initialStep, totalSteps) }
}

@Composable
fun StepIndicator(
    state: StepIndicatorState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(state.totalSteps) { index ->
            val stepNumber = index + 1
            val isActive = stepNumber <= state.currentStep
            val backgroundColor by animateColorAsState(
                targetValue = if (isActive) ComposeAppTheme.colors.jacob
                else ComposeAppTheme.colors.steel20,
                animationSpec = tween(durationMillis = 300),
                label = "stepColor$stepNumber"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(backgroundColor)
            )
        }
    }
}

@Preview
@Composable
private fun StepIndicatorPreview() {
    ComposeAppTheme {
        StepIndicator(
            state = rememberStepIndicatorState(initialStep = 1, totalSteps = 5),
            modifier = Modifier.width(200.dp)
        )
    }
}
