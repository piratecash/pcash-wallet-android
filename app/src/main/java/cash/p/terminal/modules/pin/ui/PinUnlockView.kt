package cash.p.terminal.modules.pin.ui

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.pin.unlock.PinUnlockModule
import cash.p.terminal.modules.pin.unlock.PinUnlockViewModel
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinUnlock(
    onSuccess: () -> Unit,
) {
    val viewModel = viewModel<PinUnlockViewModel>(factory = PinUnlockModule.Factory())
    val uiState = viewModel.uiState
    var showBiometricPrompt by remember {
        mutableStateOf(
            uiState.fingerScannerEnabled && uiState.inputState is PinUnlockModule.InputState.Enabled
        )
    }
    var showBiometricDisabledAlert by remember { mutableStateOf(false) }

    if (uiState.unlocked) {
        onSuccess.invoke()
        viewModel.unlocked()
    }

    if (showBiometricPrompt) {
        BiometricPromptDialog(
            onSuccess = {
                viewModel.onBiometricsUnlock()
                showBiometricPrompt = false
            },
            onError = { errorCode ->
                if (errorCode == BiometricPrompt.ERROR_LOCKOUT) {
                    showBiometricDisabledAlert = true
                }
                showBiometricPrompt = false
            }
        )
    }

    if (showBiometricDisabledAlert) {
        BiometricDisabledDialog {
            showBiometricDisabledAlert = false
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                title3_leah(
                    text = stringResource(R.string.Unlock_Title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            PinTopBlock(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.Unlock_EnterPasscode),
                enteredCount = uiState.enteredCount,
                showShakeAnimation = uiState.showShakeAnimation,
                inputState = uiState.inputState,
                onShakeAnimationFinish = { viewModel.onShakeAnimationFinish() },
            )

            PinNumpad(
                onNumberClick = { number -> viewModel.onKeyClick(number) },
                onDeleteClick = { viewModel.onDelete() },
                showFingerScanner = uiState.fingerScannerEnabled,
                pinRandomized = viewModel.pinRandomized,
                showBiometricPrompt = {
                    showBiometricPrompt = true
                },
                inputState = uiState.inputState,
                updatePinRandomized = { randomized ->
                    viewModel.updatePinRandomized(randomized)
                }
            )
        }
    }
}
