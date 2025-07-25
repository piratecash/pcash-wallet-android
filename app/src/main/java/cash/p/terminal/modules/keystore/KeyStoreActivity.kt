package cash.p.terminal.modules.keystore

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import cash.p.terminal.R
import cash.p.terminal.core.BaseActivity
import cash.p.terminal.modules.main.MainActivity
import cash.p.terminal.modules.main.MainModule
import cash.p.terminal.modules.settings.checklistterms.GeneralTermsDialog
import cash.p.terminal.modules.settings.security.ui.SystemPinBlock
import cash.p.terminal.ui.compose.components.BottomSheetsElementsButtons
import cash.p.terminal.ui.compose.components.BottomSheetsElementsHeader
import cash.p.terminal.ui.compose.components.BottomSheetsElementsText
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.parcelable
import io.horizontalsystems.core.putParcelableExtra

class KeyStoreActivity : BaseActivity() {

    private val mode by lazy {
        intent.parcelable<KeyStoreModule.ModeType>(MODE)!!
    }

    val viewModel by viewModels<KeyStoreViewModel> { KeyStoreModule.Factory(mode) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KeyStoreScreen(
                viewModel = viewModel,
                showBiometricPrompt = { showBiometricPrompt() },
                closeApp = { finish() },
                onTermsAccepted = {
                    viewModel.onTermsAccepted()

                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    this.startActivity(intent)
                    finish()
                }
            )
        }
    }

    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.OSPin_Confirm_Title))
            .setDescription(getString(R.string.OSPin_Prompt_Desciption))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfo.setAllowedAuthenticators(DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            promptInfo.setDeviceCredentialAllowed(true)
        }

        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt =
            BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.onAuthenticationSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                        || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        || errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        viewModel.onAuthenticationCanceled()
                    }
                }
            })

        biometricPrompt.authenticate(promptInfo.build())
    }

    companion object {
        const val MODE = "mode"

        fun startForNoSystemLock(context: Context) {
            start(context, KeyStoreModule.ModeType.NoSystemLock)
        }

        fun startForInvalidKey(context: Context) {
            start(context, KeyStoreModule.ModeType.InvalidKey)
        }

        fun startForUserAuthentication(context: Context) {
            start(context, KeyStoreModule.ModeType.UserAuthentication)
        }

        private fun start(context: Context, mode: KeyStoreModule.ModeType) {
            val intent = Intent(context, KeyStoreActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

                putParcelableExtra(MODE, mode)
            }

            context.startActivity(intent)
        }
    }
}

@Composable
private fun KeyStoreScreen(
    viewModel: KeyStoreViewModel,
    showBiometricPrompt: () -> Unit,
    closeApp: () -> Unit,
    onTermsAccepted: () -> Unit
) {
    if (viewModel.openMainModule) {
        viewModel.openMainModuleCalled()
        MainModule.startAsNewTask(LocalContext.current)
    }

    if (viewModel.closeApp) {
        viewModel.closeAppCalled()
        closeApp.invoke()
    }

    if (viewModel.showBiometricPrompt) {
        showBiometricPrompt.invoke()
    }

    ComposeAppTheme {
        if (viewModel.showSystemLockWarning) {
            Scaffold(
                containerColor = ComposeAppTheme.colors.tyler,
            ) { innerPaddings ->
                Column(
                    modifier = Modifier
                        .padding(innerPaddings)
                        .background(color = ComposeAppTheme.colors.tyler)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    NoSystemLockWarning(viewModel.isSystemPinRequired) {
                        viewModel.onShowTermsDialog()
                    }
                }
            }
        }

        if (viewModel.showInvalidKeyWarning) {
            KeysInvalidatedDialog { viewModel.onCloseInvalidKeyWarning() }
        }

        if (viewModel.showTermsDialog) {
            val context = LocalContext.current
            GeneralTermsDialog(
                terms = context.resources.getStringArray(R.array.system_pin_term_list).toList(),
                title = context.getString(R.string.system_pin_terms),
                confirmButtonText = context.getString(R.string.confirm),
                onClose = viewModel::onCloseTermsDialog,
                onConfirm = onTermsAccepted
            )
        }
    }
}

@Composable
private fun NoSystemLockWarning(
    isSystemPinRequired: Boolean,
    onShowTerms: () -> Unit
) {
    Column {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(id = R.drawable.ic_attention_24),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(32.dp))
        subhead2_grey(
            modifier = Modifier.padding(horizontal = 48.dp),
            text = stringResource(R.string.OSPin_Confirm_Desciption),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        SystemPinBlock(
            isPinRequired = isSystemPinRequired,
            showInfoBlock = false,
            enabled = true
        ) {
            onShowTerms()
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun KeysInvalidatedDialog(onClick: () -> Unit) {
    Dialog(onDismissRequest = onClick) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ComposeAppTheme.colors.lawrence)
        ) {
            BottomSheetsElementsHeader(
                icon = painterResource(R.drawable.icon_key_24),
                title = stringResource(R.string.Alert_KeysInvalidatedTitle),
                subtitle = stringResource(R.string.Error),
                onClickClose = onClick
            )
            BottomSheetsElementsText(
                text = stringResource(R.string.Alert_KeysInvalidatedDescription)
            )
            BottomSheetsElementsButtons(
                buttonPrimaryText = stringResource(R.string.Button_Ok),
                onClickPrimary = onClick
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview_KeysInvalidatedDialog() {
    ComposeAppTheme {
        KeysInvalidatedDialog {}
    }
}

@Preview
@Composable
private fun Preview_NoSystemWarning() {
    ComposeAppTheme {
        NoSystemLockWarning(true) {}
    }
}
