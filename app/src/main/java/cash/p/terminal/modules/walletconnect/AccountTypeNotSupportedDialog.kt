package cash.p.terminal.modules.walletconnect

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.getInput
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.BaseComposableBottomSheetFragment
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class AccountTypeNotSupportedDialog : BaseComposableBottomSheetFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
            )
            setContent {
                val navController = findNavController()

                ComposeAppTheme {
                    val input = navController.getInput<Input>()

                    AccountTypeNotSupportedScreen(
                        iconResId = input?.iconResId ?: R.drawable.ic_wallet_connect_24,
                        titleResId = input?.titleResId ?: R.string.WalletConnect_Title,
                        accountTypeDescription = input?.accountTypeDescription.orEmpty(),
                        onCloseClick = {
                            navController.popBackStack()
                        },
                        onSwitchClick = {
                            navController.popBackStack()
                            navController.slideFromRight(
                                R.id.manageAccountsFragment,
                                ManageAccountsModule.Mode.Manage
                            )
                        }
                    )
                }
            }
        }
    }

    @Parcelize
    data class Input(
        @DrawableRes val iconResId: Int,
        @StringRes val titleResId: Int,
        val accountTypeDescription: String
    ) : Parcelable
}

@Composable
private fun AccountTypeNotSupportedScreen(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    accountTypeDescription: String,
    onCloseClick: () -> Unit,
    onSwitchClick: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler
    ) { innerPadding ->
        BottomSheetHeader(
            iconPainter = painterResource(iconResId),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            title = stringResource(titleResId),
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            onCloseClick = onCloseClick
        ) {
            TextImportantWarning(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                text = stringResource(
                    id = R.string.WalletConnect_NotSupportedDescription,
                    accountTypeDescription
                )
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .padding(vertical = 20.dp, horizontal = 24.dp)
                    .fillMaxWidth(),
                title = stringResource(R.string.Button_Switch),
                onClick = onSwitchClick
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Preview
@Composable
private fun AccountTypeNotSupportedScreenPreview() {
    ComposeAppTheme {
        AccountTypeNotSupportedScreen(
            iconResId = R.drawable.ic_wallet_connect_24,
            titleResId = R.string.WalletConnect_Title,
            accountTypeDescription = "Account Type Desc",
            onCloseClick = {},
            onSwitchClick = {}
        )
    }
}
