package cash.p.terminal.modules.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.modules.main.MainModule
import cash.p.terminal.modules.pin.ConfirmPinFragment
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinFragment
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsUiState
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
import cash.p.terminal.modules.settings.security.tor.SecurityTorSettingsModule
import cash.p.terminal.modules.settings.security.tor.SecurityTorSettingsViewModel
import cash.p.terminal.modules.settings.security.ui.HardwareWalletBiometricBlock
import cash.p.terminal.modules.settings.security.ui.PasscodeBlock
import cash.p.terminal.modules.settings.security.ui.SystemPinBlock
import cash.p.terminal.modules.settings.security.ui.TorBlock
import cash.p.terminal.modules.settings.security.ui.TransactionAutoHideBlock
import cash.p.terminal.modules.settings.security.ui.TransferPasscodeBlock
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui.compose.components.HsSwitch
import cash.p.terminal.ui.extensions.ConfirmationDialog
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.SnackbarDuration
import io.horizontalsystems.core.helpers.HudHelper
import io.horizontalsystems.core.slideFromBottomForResult
import io.horizontalsystems.core.ui.dialogs.ChecklistConfirmationDialog
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.system.exitProcess

class SecuritySettingsFragment : BaseComposeFragment() {

    private val torViewModel by viewModels<SecurityTorSettingsViewModel> {
        SecurityTorSettingsModule.Factory()
    }

    private val securitySettingsViewModel by viewModel<SecuritySettingsViewModel>()

    @Composable
    override fun GetContent(navController: NavController) {
        SecurityCenterScreen(
            securitySettingsViewModel = securitySettingsViewModel,
            uiState = securitySettingsViewModel.uiState,
            torViewModel = torViewModel,
            navController = navController,
            onTransactionAutoHideEnabledChange = { enabled ->
                if (enabled) {
                    if (securitySettingsViewModel.uiState.pinEnabled) {
                        securitySettingsViewModel.onTransactionAutoHideEnabledChange(true)
                    } else {
                        navController.ensurePinSet(R.string.PinSet_Title) {
                            securitySettingsViewModel.onTransactionAutoHideEnabledChange(true)
                        }
                    }
                } else {
                    navController.authorizedAction(
                        ConfirmPinFragment.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                            pinType = PinType.TRANSACTIONS_HIDE
                        )
                    ) {
                        securitySettingsViewModel.onTransactionAutoHideEnabledChange(false)
                    }
                }
            },
            onChangeDisplayClicked = {
                navController.authorizedAction(
                    ConfirmPinFragment.InputConfirm(
                        descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                        pinType = PinType.TRANSACTIONS_HIDE
                    )
                ) {
                    navController.slideFromRight(R.id.chooseDisplayTransactionsFragment)
                }
            },
            onSetTransactionAutoHidePinClicked = {
                if (!securitySettingsViewModel.uiState.transactionAutoHideSeparatePinExists) {
                    navController.authorizedAction(
                        ConfirmPinFragment.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode,
                            pinType = PinType.REGULAR
                        )
                    ) {
                        navController.slideFromRight(
                            R.id.setPinFragment,
                            SetPinFragment.Input(
                                descriptionResId = R.string.PinSet_Transactions_Hide,
                                pinType = PinType.TRANSACTIONS_HIDE
                            )
                        )
                    }
                } else {
                    navController.authorizedAction(
                        ConfirmPinFragment.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                            pinType = PinType.TRANSACTIONS_HIDE
                        )
                    ) {
                        navController.slideFromRight(
                            resId = R.id.editPinFragment,
                            input = SetPinFragment.Input(
                                R.string.PinSet_Transactions_Hide,
                                PinType.TRANSACTIONS_HIDE
                            )
                        )
                    }
                }
            },
            onDisableTransactionAutoHidePinClicked = {
                navController.authorizedAction(
                    ConfirmPinFragment.InputConfirm(
                        descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                        pinType = PinType.TRANSACTIONS_HIDE
                    )
                ) {
                    securitySettingsViewModel.onDisableTransactionAutoHidePin()
                }
            },
            showAppRestartAlert = { showAppRestartAlert() },
            restartApp = { restartApp() },
            onTransferPasscodeEnabledChange = { enabled ->
                if (enabled) {
                    if (securitySettingsViewModel.uiState.pinEnabled) {
                        securitySettingsViewModel.onTransferPasscodeEnabledChange(true)
                    } else {
                        navController.ensurePinSet(R.string.PinSet_Title) {
                            securitySettingsViewModel.onTransferPasscodeEnabledChange(true)
                        }
                    }
                } else {
                    navController.authorizedAction(
                        ConfirmPinFragment.InputConfirm(
                            descriptionResId = R.string.Unlock_EnterPasscode_Transfer,
                            pinType = PinType.REGULAR
                        )
                    ) {
                        securitySettingsViewModel.onTransferPasscodeEnabledChange(false)
                    }
                }
            },
            onEnableSaveAccessCodeForHardwareWallet = securitySettingsViewModel::enableSaveAccessCodeForHardwareWallet,
            onSystemPinRequiredChange = securitySettingsViewModel::onSystemPinRequiredChange
        )
    }

    private fun showAppRestartAlert() {
        val warningTitle = if (torViewModel.torCheckEnabled) {
            getString(R.string.Tor_Connection_Enable)
        } else {
            getString(R.string.Tor_Connection_Disable)
        }

        val actionButton = if (torViewModel.torCheckEnabled) {
            getString(R.string.Button_Enable)
        } else {
            getString(R.string.Button_Disable)
        }

        ConfirmationDialog.show(
            icon = R.drawable.ic_tor_connection_24,
            title = getString(R.string.Tor_Alert_Title),
            warningTitle = warningTitle,
            warningText = getString(R.string.SettingsSecurity_AppRestartWarning),
            actionButtonTitle = actionButton,
            transparentButtonTitle = getString(R.string.Alert_Cancel),
            fragmentManager = childFragmentManager,
            listener = object : ConfirmationDialog.Listener {
                override fun onActionButtonClick() {
                    torViewModel.setTorEnabled()
                }

                override fun onTransparentButtonClick() {
                    torViewModel.resetSwitch()
                }

                override fun onCancelButtonClick() {
                    torViewModel.resetSwitch()
                }
            }
        )
    }

    private fun restartApp() {
        activity?.let {
            MainModule.startAsNewTask(it)
            exitProcess(0)
        }
    }
}

@Composable
private fun SecurityCenterScreen(
    securitySettingsViewModel: SecuritySettingsViewModel,
    uiState: SecuritySettingsUiState,
    torViewModel: SecurityTorSettingsViewModel,
    navController: NavController,
    onTransactionAutoHideEnabledChange: (Boolean) -> Unit,
    onSetTransactionAutoHidePinClicked: () -> Unit,
    onDisableTransactionAutoHidePinClicked: () -> Unit,
    onChangeDisplayClicked: () -> Unit,
    onTransferPasscodeEnabledChange: (Boolean) -> Unit,
    showAppRestartAlert: () -> Unit,
    restartApp: () -> Unit,
    onEnableSaveAccessCodeForHardwareWallet: (Boolean) -> Unit,
    onSystemPinRequiredChange: (Boolean) -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val context = LocalContext.current
    val view = LocalView.current
    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
        securitySettingsViewModel.update()
    }

    if (torViewModel.restartApp) {
        restartApp()
        torViewModel.appRestarted()
    }

    val uiState = securitySettingsViewModel.uiState
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Settings_SecurityCenter),
                navigationIcon = {
                    HsBackButton(onClick = { navController.popBackStack() })
                },
            )
        }
    ) {
        Column(
            Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(windowInsets)
        ) {
            PasscodeBlock(
                securitySettingsViewModel,
                navController
            )

            VSpacer(height = 32.dp)

            CellUniversalLawrenceSection {
                SecurityCenterCell(
                    start = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_off_24),
                            tint = ComposeAppTheme.colors.grey,
                            modifier = Modifier.size(24.dp),
                            contentDescription = null
                        )
                    },
                    center = {
                        body_leah(
                            text = stringResource(id = R.string.Appearance_BalanceAutoHide),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    end = {
                        HsSwitch(
                            checked = uiState.balanceAutoHideEnabled,
                            onCheckedChange = {
                                securitySettingsViewModel.onSetBalanceAutoHidden(it)
                            }
                        )
                    }
                )
            }
            InfoText(
                text = stringResource(R.string.Appearance_BalanceAutoHide_Description),
                paddingBottom = 32.dp
            )

            TransactionAutoHideBlock(
                transactionAutoHideEnabled = uiState.transactionAutoHideEnabled,
                displayLevel = uiState.displayLevel,
                transactionAutoHideSeparatePinExists = uiState.transactionAutoHideSeparatePinExists,
                onTransactionAutoHideEnabledChange = onTransactionAutoHideEnabledChange,
                onPinClicked = onSetTransactionAutoHidePinClicked,
                onDisablePinClicked = onDisableTransactionAutoHidePinClicked,
                onChangeDisplayClicked = onChangeDisplayClicked
            )

            TransferPasscodeBlock(
                transferPasscodeEnabled = uiState.transferPasscodeEnabled,
                onTransferPasscodeEnabledChange = onTransferPasscodeEnabledChange
            )

            TorBlock(
                torViewModel,
                showAppRestartAlert,
            )

            HardwareWalletBiometricBlock(
                enabled = securitySettingsViewModel.uiState.isSaveAccessCodeForHardwareWalletEnabled,
                onValueChanged = onEnableSaveAccessCodeForHardwareWallet
            )

            DuressPasscodeBlock(
                securitySettingsViewModel,
                navController
            )
            InfoText(
                text = stringResource(R.string.SettingsSecurity_DuressPinDescription),
                paddingBottom = 32.dp
            )

            SystemPinBlock(
                isPinRequired = uiState.isSystemPinRequired,
                enabled = uiState.isSystemPinRequiredEnabled,
                onPinRequiredChange = { checked ->
                    if (!uiState.isSystemPinRequired) {
                        if (!uiState.isDeviceSecure) {
                            HudHelper.showWarningMessage(
                                contentView = view,
                                resId = R.string.need_setup_system_pin,
                                duration = SnackbarDuration.LONG
                            )
                        } else
                            navController.slideFromBottomForResult<ChecklistConfirmationDialog.Result>(
                                resId = R.id.checklistConfirmationDialog,
                                input = ChecklistConfirmationDialog.ChecklistConfirmationInput(
                                    title = context.getString(R.string.SettingsSecurity_system_pin_enable),
                                    confirmButtonText = context.getString(R.string.confirm),
                                    items = context.resources.getStringArray(R.array.enable_system_pin_confirm)
                                        .toList()
                                )
                            ) { result ->
                                if (result.confirmed) {
                                    onSystemPinRequiredChange(true)
                                }
                            }
                    }
                }
            )
        }
    }
}

@Composable
fun SecurityCenterCell(
    start: @Composable RowScope.() -> Unit,
    center: @Composable RowScope.() -> Unit,
    end: @Composable() (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        start.invoke(this)
        Spacer(Modifier.width(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            center(this)
        }
        end?.let {
            Spacer(
                Modifier
                    .defaultMinSize(minWidth = 8.dp)
            )
            end.invoke(this)
        }
    }
}