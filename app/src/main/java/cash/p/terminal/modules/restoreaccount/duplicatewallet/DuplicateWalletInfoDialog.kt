package cash.p.terminal.modules.restoreaccount.duplicatewallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.ui_compose.BaseComposableBottomSheetFragment
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

internal class DuplicateWalletInfoDialog : BaseComposableBottomSheetFragment() {
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
                    DuplicateWalletInfoScreen(navController)
                }
            }
        }
    }
}

@Composable
private fun DuplicateWalletInfoScreen(navController: NavController) {
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_copy_24px),
        title = stringResource(R.string.duplicate_wallet),
        onCloseClick = {
            navController.popBackStack()
        }
    ) {
        body_leah(
            text = stringResource(R.string.duplicate_wallet_info),
            modifier = Modifier
                .padding(16.dp)
                .border(1.dp, ComposeAppTheme.colors.jeremy,
                    RoundedCornerShape(12.dp))
                .padding(16.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun DuplicateWalletInfoScreenPreview() {
    ComposeAppTheme {
        DuplicateWalletInfoScreen(navController = rememberNavController())
    }
}
