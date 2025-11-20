package cash.p.terminal.modules.send.securitycheck

import android.os.Parcelable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.core.adapters.stellar.StellarAssetAdapter
import cash.p.terminal.core.address.AddressCheckType
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.modules.address.HSAddressCell
import cash.p.terminal.modules.pin.ConfirmPinFragment
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.send.SendConfirmationFragment
import cash.p.terminal.modules.send.address.AddressCheckResult
import cash.p.terminal.modules.send.address.AddressEnterInfoBottomSheet
import cash.p.terminal.modules.send.address.AddressValidationError
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.components.subhead2_remus
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.theme.YellowL
import cash.p.terminal.wallet.Wallet
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class SecurityCheckFragment : BaseComposeFragment() {

    private val args: SecurityCheckFragmentArgs by navArgs()

    @Composable
    override fun GetContent(navController: NavController) {
        SecurityCheckScreen(navController, args.input)
    }

    @Parcelize
    data class SecurityCheckInput(
        val address: String,
        val wallet: Wallet,
        val type: SendConfirmationFragment.Type,
        val sendEntryPointDestId: Int
    ) : Parcelable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCheckScreen(
    navController: NavController,
    input: SecurityCheckFragment.SecurityCheckInput
) {
    val viewModel = viewModel<SecurityCheckViewModel>(
        factory = SecurityCheckViewModel.Factory(
            token = input.wallet.token,
            address = input.address,
            addressCheckerSkippable = true
        )
    )

    val coroutineScope = rememberCoroutineScope()
    val infoModalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var checkTypeInfoBottomSheet by remember { mutableStateOf<AddressCheckType?>(null) }

    val uiState = viewModel.uiState
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.security_report),
                navigationIcon = {
                    HsBackButton(onClick = { navController.popBackStack() })
                },
            )
        },
    ) { innerPaddings ->
        Column(
            modifier = Modifier
                .padding(innerPaddings)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                HSAddressCell(
                    title = stringResource(R.string.Send_Confirmation_To),
                    value = input.address,
                    riskyAddress = false,
                    onClick = navController::popBackStack
                )
                VSpacer(12.dp)

                AddressCheck(
                    uiState.addressValidationInProgress,
                    uiState.addressValidationError,
                    uiState.checkResults,
                ) { checkType ->
                    if (uiState.checkResults.any { it.value.checkResult == AddressCheckResult.NotAllowed }) {
                        navController.slideFromBottom(R.id.aboutPremiumFragment)
                    } else {
                        checkTypeInfoBottomSheet = checkType
                        coroutineScope.launch {
                            infoModalBottomSheetState.show()
                        }
                    }
                }
            }
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                title = if (uiState.addressValidationError != null)
                    stringResource(R.string.Send_Address_Error_InvalidAddress)
                else
                    stringResource(R.string.Button_Next),
                onClick = {
                    navController.authorizedAction(
                        ConfirmPinFragment.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode,
                            pinType = PinType.TRANSFER
                        )
                    ) {
                        navController.slideFromRight(
                            MainGraphDirections.actionGlobalToSendConfirmationFragment(
                                input.type,
                                input.sendEntryPointDestId
                            )
                        )
                    }
                },
                enabled = uiState.canBeSendToAddress
            )
        }
    }

    checkTypeInfoBottomSheet?.let { checkType ->
        AddressEnterInfoBottomSheet(
            checkType = checkType,
            bottomSheetState = infoModalBottomSheetState,
            hideBottomSheet = {
                coroutineScope.launch {
                    infoModalBottomSheetState.hide()
                }
                checkTypeInfoBottomSheet = null
            }
        )
    }
}

@Composable
fun AddressCheck(
    addressValidationInProgress: Boolean,
    addressValidationError: Throwable?,
    checkResults: Map<AddressCheckType, AddressCheckData>,
    onClick: (type: AddressCheckType) -> Unit
) {
    if (addressValidationInProgress) {
        AddressCheckInProgress(
            Modifier
                .padding(horizontal = 16.dp)
        )
    } else if (addressValidationError != null) {
        val errorMessage = addressValidationError.getErrorMessage()
        if (errorMessage != null) {
            TextImportantError(
                modifier = Modifier.padding(horizontal = 16.dp),
                icon = R.drawable.ic_attention_20,
                title = stringResource(R.string.SwapSettings_Error_InvalidAddress),
                text = errorMessage
            )
            VSpacer(32.dp)
        }
    }

    if (checkResults.isNotEmpty()) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    0.5.dp,
                    ComposeAppTheme.colors.steel20,
                    RoundedCornerShape(12.dp)
                )
        ) {
            checkResults.forEach { (addressCheckType, checkData) ->
                CheckCell(
                    title = stringResource(addressCheckType.title),
                    checkType = addressCheckType,
                    inProgress = checkData.inProgress,
                    checkResult = checkData.checkResult,
                    onClick
                )
            }
        }
    } else if (!addressValidationInProgress) {
        NoProblemsFound(Modifier
            .padding(horizontal = 16.dp)
        )
    }

    checkResults.forEach { (addressCheckType, addressCheckData) ->
        if (addressCheckData.checkResult == AddressCheckResult.Detected) {
            TextImportantError(
                modifier = Modifier.padding(horizontal = 16.dp),
                icon = R.drawable.ic_attention_20,
                title = stringResource(addressCheckType.detectedErrorTitle),
                text = stringResource(addressCheckType.detectedErrorDescription)
            )
            VSpacer(16.dp)
        }
    }

    if (checkResults.any { it.value.checkResult == AddressCheckResult.Detected }) {
        VSpacer(32.dp)
    }
}

@Composable
private fun Throwable.getErrorMessage() = when (this) {
    is StellarAssetAdapter.NoTrustlineError -> {
        stringResource(R.string.Error_AssetNotEnabled, code)
    }

    is AddressValidationError -> this.message
    else -> null
}

@Composable
private fun CheckCell(
    title: String,
    checkType: AddressCheckType,
    inProgress: Boolean,
    checkResult: AddressCheckResult,
    onClick: (type: AddressCheckType) -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick(checkType) }
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checkType == AddressCheckType.SmartContract) {
            Icon(
                painter = painterResource(R.drawable.ic_star_filled_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.jacob,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp)
            )
        }
        subhead2_grey(text = title)
        Spacer(Modifier.weight(1f))
        if (checkResult == AddressCheckResult.NotAllowed) {
            CheckLocked()
        } else {
            CheckValue(inProgress, checkResult)
        }
    }
}

@Composable
fun CheckValue(
    inProgress: Boolean,
    checkResult: AddressCheckResult,
) {
    if (inProgress) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = ComposeAppTheme.colors.grey,
            strokeWidth = 2.dp
        )
    } else {
        when (checkResult) {
            AddressCheckResult.Clear -> {
                subhead2_remus(stringResource(checkResult.title))
            }

            AddressCheckResult.Detected -> {
                subhead2_lucian(stringResource(checkResult.title))
            }

            AddressCheckResult.AlphaAmlVeryLow -> {
                subhead2_remus(stringResource(checkResult.title))
            }

            AddressCheckResult.AlphaAmlLow -> {
                subhead2(stringResource(checkResult.title), ComposeAppTheme.colors.yellowD)
            }

            AddressCheckResult.AlphaAmlHigh -> {
                subhead2(stringResource(checkResult.title), YellowL)
            }

            AddressCheckResult.AlphaAmlVeryHigh -> {
                subhead2_lucian(stringResource(checkResult.title))
            }

            AddressCheckResult.NotAllowed,
            AddressCheckResult.NotAvailable,
            AddressCheckResult.NotSupported -> subhead2_grey(stringResource(R.string.NotAvailable))
        }
    }
}

@Composable
fun CheckLocked() {
    Icon(
        painter = painterResource(R.drawable.ic_lock_20),
        contentDescription = null,
        tint = ComposeAppTheme.colors.andy,
    )
}

@Composable
private fun AddressCheckInProgress(modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = ComposeAppTheme.colors.grey,
            strokeWidth = 2.dp
        )
        HSpacer(8.dp)
        subhead2_grey(text = stringResource(R.string.checking_address))
    }
}

@Composable
private fun NoProblemsFound(modifier: Modifier = Modifier) {
    subhead2_grey(
        text = stringResource(R.string.no_checks_available),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}
