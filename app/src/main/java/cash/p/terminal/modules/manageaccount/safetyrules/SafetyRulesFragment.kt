package cash.p.terminal.modules.manageaccount.safetyrules

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import cash.p.terminal.R
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.ui_compose.BaseComposeFragment
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class SafetyRulesFragment : BaseComposeFragment() {

    private val args: SafetyRulesFragmentArgs by navArgs()

    @Composable
    override fun GetContent(navController: NavController) {
        val termTitles = listOf(
            getString(R.string.safety_rules_checkbox_1),
            getString(R.string.safety_rules_checkbox_2),
            getString(R.string.safety_rules_checkbox_3)
        )

        val viewModel = koinViewModel<SafetyRulesViewModel> {
            parametersOf(args.input.mode, termTitles)
        }

        SafetyRulesScreen(
            uiState = viewModel.uiState,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = {
                viewModel.agree()
                navController.setNavigationResultX(Result.AGREED)
                navController.popBackStack()
            },
            onRiskItClick = {
                navController.setNavigationResultX(Result.RISK_IT)
                navController.popBackStack()
            },
            onCancelClick = {
                navController.setNavigationResultX(Result.CANCELLED)
                navController.popBackStack()
            }
        )
    }

    @Parcelize
    enum class Result : Parcelable {
        AGREED,
        RISK_IT,
        CANCELLED
    }
}
