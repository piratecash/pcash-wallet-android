package cash.p.terminal.modules.enablecoin.restoresettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureFragment
import cash.p.terminal.modules.zcashconfigure.ZcashConfigureFragment
import cash.p.terminal.navigation.slideFromBottomForResult
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.delay

@Composable
fun NavController.openRestoreSettingsDialog(
    token: Token,
    restoreSettingsViewModel: IRestoreSettingsUi
) {
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        keyboard?.hide()
        // Wait for keyboard to hide to avoid UI glitches
        delay(300)

        val initialConfig = restoreSettingsViewModel.consumeInitialConfig()
        restoreSettingsViewModel.tokenConfigureOpened()

        when (token.blockchainType) {
            BlockchainType.Zcash -> {
                slideFromBottomForResult<ZcashConfigureFragment.Result>(
                    resId = R.id.zcashConfigureFragment,
                    input = ZcashConfigureFragment.Input(initialConfig)
                ) { result ->
                    if (result.config != null) {
                        restoreSettingsViewModel.onEnter(result.config)
                    } else {
                        restoreSettingsViewModel.onCancelEnterBirthdayHeight()
                    }
                }
            }

            BlockchainType.Monero -> {
                slideFromBottomForResult<MoneroConfigureFragment.Result>(
                    resId = R.id.moneroConfigure,
                    input = MoneroConfigureFragment.Input(initialConfig)
                ) { result ->
                    if (result.config != null) {
                        restoreSettingsViewModel.onEnter(result.config)
                    } else {
                        restoreSettingsViewModel.onCancelEnterBirthdayHeight()
                    }
                }
            }

            else -> Unit
        }
    }
}
