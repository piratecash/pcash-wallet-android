package cash.p.terminal.feature.miniapp.ui.connect.screens

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.miniapp.ui.components.CaptchaCodeInput
import cash.p.terminal.feature.miniapp.ui.components.JwtExpiredStepContent
import cash.p.terminal.feature.miniapp.ui.components.MiniAppStepScaffold
import cash.p.terminal.feature.miniapp.ui.components.StepDescriptionStyle
import cash.p.terminal.feature.miniapp.ui.components.StepIndicatorState
import cash.p.terminal.feature.miniapp.ui.components.rememberStepIndicatorState
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonSecondary
import cash.p.terminal.ui_compose.components.SecondaryButtonDefaults
import cash.p.terminal.ui_compose.components.VSpacer
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay

private const val CODE_LENGTH = 5

@Composable
fun CaptchaStepScreen(
    captchaImageBase64: String?,
    expiresInSeconds: Long,
    code: String,
    error: String?,
    isLoading: Boolean,
    isVerifying: Boolean,
    isJwtExpired: Boolean,
    onCodeChange: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onVerifyClick: () -> Unit,
    onOpenMiniAppClick: () -> Unit,
    stepIndicatorState: StepIndicatorState? = null,
    modifier: Modifier = Modifier
) {
    // Show link expired state
    if (isJwtExpired) {
        JwtExpiredStepContent(
            stepTitle = stringResource(R.string.connect_mini_app_step_4),
            stepIndicatorState = stepIndicatorState,
            onOpenMiniAppClick = onOpenMiniAppClick,
            modifier = modifier
        )
        return
    }

    // Countdown timer state
    var remainingSeconds by remember(expiresInSeconds) { mutableLongStateOf(expiresInSeconds) }

    // Countdown effect
    LaunchedEffect(expiresInSeconds) {
        remainingSeconds = expiresInSeconds
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }

    val isTimerExpired = remainingSeconds <= 0
    val isCodeComplete = code.length == CODE_LENGTH

    MiniAppStepScaffold(
        stepTitle = stringResource(R.string.connect_mini_app_step_4),
        stepDescription = stringResource(R.string.connect_mini_app_step_4_description),
        descriptionStyle = StepDescriptionStyle.Grey,
        stepIndicatorState = stepIndicatorState,
        modifier = modifier,
        bottomContent = {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.connect_mini_app_captcha_send),
                onClick = onVerifyClick,
                enabled = isCodeComplete && !isVerifying && !isLoading
            )
        },
        content = {
            Spacer(Modifier.weight(1f))
            Column(Modifier.imePadding()) {

                // Captcha image container
                Box(
                    modifier = Modifier
                        .padding(horizontal = 55.dp)
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ComposeAppTheme.colors.light)
                        .border(1.dp, ComposeAppTheme.colors.steel20, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = ComposeAppTheme.colors.jacob,
                                strokeWidth = 2.dp
                            )
                        }

                        captchaImageBase64 != null -> {
                            val imageBitmap = remember(captchaImageBase64) {
                                decodeBase64Image(captchaImageBase64)
                            }
                            if (imageBitmap != null) {
                                Image(
                                    bitmap = imageBitmap.asImageBitmap(),
                                    contentDescription = "Captcha",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                subhead2_grey(text = "Failed to load image")
                            }
                        }

                        else -> {
                            subhead2_grey(text = "No captcha loaded")
                        }
                    }
                }

                val timerText = formatTime(remainingSeconds)
                val timerColor = if (isTimerExpired) {
                    ComposeAppTheme.colors.lucian
                } else {
                    ComposeAppTheme.colors.grey
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 55.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ButtonSecondary(
                        onClick = onRefreshClick,
                        enabled = !isLoading,
                        buttonColors = SecondaryButtonDefaults.buttonColors(
                            backgroundColor = ComposeAppTheme.colors.transparent,
                            contentColor = ComposeAppTheme.colors.grey,
                            disabledBackgroundColor = ComposeAppTheme.colors.transparent,
                            disabledContentColor = ComposeAppTheme.colors.grey50,
                        )
                    ) {
                        Icon(
                            modifier = Modifier.padding(end = 8.dp),
                            painter = painterResource(R.drawable.ic_refresh_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.grey
                        )
                        Text(
                            text = stringResource(R.string.connect_mini_app_captcha_update),
                            maxLines = 1,
                            color = ComposeAppTheme.colors.grey
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = timerText,
                        style = ComposeAppTheme.typography.subhead1,
                        color = timerColor
                    )
                }

                VSpacer(24.dp)

                // Code input
                CaptchaCodeInput(
                    code = code,
                    onCodeChange = onCodeChange,
                    onDone = {
                        if (isCodeComplete && !isVerifying && !isLoading) {
                            onVerifyClick()
                        }
                    },
                    isError = error != null,
                    errorMessage = error,
                    enabled = !isLoading && captchaImageBase64 != null,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(Modifier.weight(2f))
        }
    )
}

private fun decodeBase64Image(base64String: String): android.graphics.Bitmap? {
    return try {
        // Remove data URL prefix if present
        val base64Data = if (base64String.contains(",")) {
            base64String.substringAfter(",")
        } else {
            base64String
        }
        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

private fun formatTime(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val minutes = seconds / 60
    val secs = seconds % 60
    return "$minutes:${secs.toString().padStart(2, '0')}"
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaStepScreenLoadingPreview() {
    ComposeAppTheme {
        CaptchaStepScreen(
            captchaImageBase64 = null,
            expiresInSeconds = 300,
            code = "",
            error = null,
            isLoading = true,
            isVerifying = false,
            isJwtExpired = false,
            onCodeChange = {},
            onRefreshClick = {},
            onVerifyClick = {},
            onOpenMiniAppClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 4)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaStepScreenWithCodePreview() {
    ComposeAppTheme {
        CaptchaStepScreen(
            captchaImageBase64 = null,
            expiresInSeconds = 120,
            code = "A7K",
            error = null,
            isLoading = false,
            isVerifying = false,
            isJwtExpired = false,
            onCodeChange = {},
            onRefreshClick = {},
            onVerifyClick = {},
            onOpenMiniAppClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 4)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaStepScreenErrorPreview() {
    ComposeAppTheme {
        CaptchaStepScreen(
            captchaImageBase64 = null,
            expiresInSeconds = 0,
            code = "A7K9Q",
            error = "Wrong code, please try again",
            isLoading = false,
            isVerifying = false,
            isJwtExpired = false,
            onCodeChange = {},
            onRefreshClick = {},
            onVerifyClick = {},
            onOpenMiniAppClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 4)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CaptchaStepScreenJwtExpiredPreview() {
    ComposeAppTheme {
        CaptchaStepScreen(
            captchaImageBase64 = null,
            expiresInSeconds = 0,
            code = "",
            error = null,
            isLoading = false,
            isVerifying = false,
            isJwtExpired = true,
            onCodeChange = {},
            onRefreshClick = {},
            onVerifyClick = {},
            onOpenMiniAppClick = {},
            stepIndicatorState = rememberStepIndicatorState(initialStep = 4)
        )
    }
}
