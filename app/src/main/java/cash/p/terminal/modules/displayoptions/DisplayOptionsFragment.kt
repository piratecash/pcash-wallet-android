package cash.p.terminal.modules.displayoptions

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.ui_compose.BaseComposeFragment
import kotlinx.parcelize.Parcelize
import org.koin.androidx.viewmodel.ext.android.viewModel

class DisplayOptionsFragment : BaseComposeFragment() {
    private val viewModel: DisplayOptionsViewModel by viewModel()

    @Composable
    override fun GetContent(navController: NavController) {
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        navController.setNavigationResultX(Result())
        DisplayOptionsScreen(
            navController = navController,
            uiState = uiState.value,
            onPricePeriodChanged = viewModel::onPricePeriodChanged,
            onPercentChangeToggled = viewModel::onPercentChangeToggled,
            onPriceChangeToggled = viewModel::onPriceChangeToggled
        )
    }

    @Parcelize
    class Result : Parcelable
}
