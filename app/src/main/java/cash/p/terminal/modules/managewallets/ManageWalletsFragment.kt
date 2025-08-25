package cash.p.terminal.modules.managewallets

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsViewModel
import cash.p.terminal.navigation.slideFromBottomForResult
import cash.p.terminal.ui.dialogs.CancelOrScanDialog
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.findNavController

class ManageWalletsFragment : BaseComposeFragment() {

    private val vmFactory by lazy { ManageWalletsModule.Factory() }
    private val viewModel by viewModels<ManageWalletsViewModel> { vmFactory }
    private val restoreSettingsViewModel by viewModels<RestoreSettingsViewModel> { vmFactory }

    @Composable
    override fun GetContent(navController: NavController) {
        ManageWalletsScreen(
            navController = navController,
            manageWalletsCallback = viewModel,
            onBackPressed = {
                if (viewModel.showScanToAddButton) {
                    showScanOrCancelDialog()
                } else {
                    navController.popBackStack()
                }
            },
            requestScan = {
                viewModel.requestScanToAddTokens(false)
            },
            restoreSettingsViewModel = restoreSettingsViewModel
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle back press for hardware wallet
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.showScanToAddButton) {
                        showScanOrCancelDialog()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed() // Trigger the back press again, which will now be handled by other callbacks or the default system behavior.
                    }
                }
            })
    }

    private fun showScanOrCancelDialog() {
        findNavController().slideFromBottomForResult<CancelOrScanDialog.Result>(
            R.id.cancelOrScanDialog
        ) { result ->
            if (result.confirmed) {
                viewModel.requestScanToAddTokens(true)
            } else {
                findNavController().popBackStack()
            }
        }
    }
}