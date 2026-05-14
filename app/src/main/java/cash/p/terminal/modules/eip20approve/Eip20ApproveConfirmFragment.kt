package cash.p.terminal.modules.eip20approve

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.rememberViewModelFromGraph
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.eip20approve.AllowanceMode.OnlyRequired
import cash.p.terminal.modules.eip20approve.AllowanceMode.Unlimited
import cash.p.terminal.modules.evmfee.Cautions
import cash.p.terminal.modules.multiswap.TokenRow
import cash.p.terminal.modules.multiswap.TokenRowUnlimited
import cash.p.terminal.modules.fee.DataFieldFee
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui.compose.components.TransactionInfoAddressCell
import cash.p.terminal.ui.compose.components.TransactionInfoContactCell
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.cell.BoxBorderedTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class Eip20ApproveConfirmFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        withInput<Eip20ApproveFragment.Input>(navController) { input ->
            Eip20ApproveConfirmScreen(navController, input)
        }
    }

    @Parcelize
    data class Result(val approved: Boolean) : Parcelable
}

@Composable
internal fun Eip20ApproveConfirmScreen(
    navController: NavController,
    input: Eip20ApproveFragment.Input
) {
    val viewModel =
        rememberViewModelFromGraph<Eip20ApproveViewModel>(
            navController,
            R.id.eip20ApproveFragment,
            Eip20ApproveViewModel.Factory(
                input.token,
                input.requiredAllowance,
                input.spenderAddress
            )
        )
            ?: return

    val uiState = viewModel.uiState

    ConfirmTransactionScreen(
        onClickBack = navController::popBackStackSafely,
        onClickSettings = {
            navController.slideFromRight(
                R.id.eip20ApproveTransactionSettingsFragment,
                uiState.toInput()
            )
        },
        onClickClose = {
            navController.popBackStack(R.id.eip20ApproveFragment, true)
        },
        buttonsSlot = {
            Eip20ApproveConfirmButtons(
                onApprove = viewModel::approve,
                onResult = navController::finishApproveFlow,
                onCancel = {
                    navController.popBackStack(R.id.eip20ApproveFragment, true)
                },
                approveEnabled = uiState.approveEnabled
            )
        }
    ) {
        Eip20ApproveConfirmContent(uiState, navController)
    }
}

private fun NavController.finishApproveFlow(result: Eip20ApproveConfirmFragment.Result) {
    if (!popBackStack()) return

    // The approve screen is current after popping confirm, so this targets the original swap caller.
    setNavigationResultX(result)
    popBackStack()
}

@Composable
private fun Eip20ApproveConfirmButtons(
    onApprove: suspend () -> Unit,
    onResult: (Eip20ApproveConfirmFragment.Result) -> Unit,
    onCancel: () -> Unit,
    approveEnabled: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    var buttonEnabled by remember { mutableStateOf(true) }
    val view = LocalView.current

    Column {
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.Swap_Approve),
            onClick = {
                coroutineScope.launch {
                    buttonEnabled = false
                    HudHelper.showInProcessMessage(
                        view,
                        R.string.Swap_Approving,
                        SnackbarDuration.INDEFINITE
                    )

                    val result = try {
                        onApprove()

                        HudHelper.showSuccessMessage(view, R.string.Hud_Text_Done)
                        delay(1200)
                        Eip20ApproveConfirmFragment.Result(true)
                    } catch (t: Throwable) {
                        val msg = (t as? IllegalStateException)?.message ?: t.javaClass.simpleName
                        HudHelper.showErrorMessage(view, msg)
                        Eip20ApproveConfirmFragment.Result(false)
                    }

                    buttonEnabled = true
                    onResult(result)
                }
            },
            enabled = approveEnabled && buttonEnabled
        )
        VSpacer(16.dp)
        ButtonPrimaryDefault(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.Button_Cancel),
            onClick = onCancel
        )
    }
}

@Composable
private fun Eip20ApproveConfirmContent(
    uiState: Eip20ApproveUiState,
    navController: NavController
) {
    Eip20ApproveTokenSection(uiState, navController)

    VSpacer(height = 16.dp)
    SectionUniversalLawrence {
        DataFieldFee(
            uiState.networkFee?.primary?.getFormattedPlain() ?: "---",
            uiState.networkFee?.secondary?.getFormattedPlain() ?: "---"
        )
    }

    if (uiState.cautions.isNotEmpty()) {
        Cautions(cautions = uiState.cautions)
    }
}

@Composable
private fun Eip20ApproveTokenSection(
    uiState: Eip20ApproveUiState,
    navController: NavController
) {
    SectionUniversalLawrence {
        when (uiState.allowanceMode) {
            OnlyRequired -> {
                TokenRow(
                    token = uiState.token,
                    amount = uiState.requiredAllowance,
                    fiatAmount = uiState.fiatAmount,
                    currency = uiState.currency,
                    borderTop = false,
                    title = stringResource(R.string.Approve_YouApprove),
                    amountColor = ComposeAppTheme.colors.leah
                )
            }

            Unlimited -> {
                TokenRowUnlimited(
                    token = uiState.token,
                    borderTop = false,
                    title = stringResource(R.string.Approve_YouApprove),
                    amountColor = ComposeAppTheme.colors.leah
                )
            }
        }

        BoxBorderedTop {
            TransactionInfoAddressCell(
                title = stringResource(R.string.Approve_Spender),
                value = uiState.spenderAddress,
                showAdd = uiState.contact == null,
                blockchainType = uiState.token.blockchainType,
                navController = navController
            )
        }

        uiState.contact?.let {
            BoxBorderedTop {
                TransactionInfoContactCell(it.name)
            }
        }
    }
}

private fun Eip20ApproveUiState.toInput() = Eip20ApproveFragment.Input(
    token,
    requiredAllowance,
    spenderAddress
)
