package cash.p.terminal.modules.premium.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.paging.compose.collectAsLazyPagingItems
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.authorizedLoggingAction
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.core.ensurePinSetPremiumAction
import cash.p.terminal.core.premiumAction
import cash.p.terminal.feature.logging.detail.LoggingDetailScreen
import cash.p.terminal.feature.logging.detail.LoggingDetailViewModel
import cash.p.terminal.feature.logging.history.LoggingListScreen
import cash.p.terminal.feature.logging.history.LoggingListViewModel
import cash.p.terminal.feature.logging.settings.LoggingSettingsScreen
import cash.p.terminal.feature.logging.settings.LoggingSettingsViewModel
import cash.p.terminal.modules.pin.ConfirmPinFragment
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.premium.smsnotification.SendSmsNotificationScreen
import cash.p.terminal.modules.premium.smsnotification.SendSmsNotificationViewModel
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowWithArrow
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class PremiumSettingsFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        PremiumSettingsNavHost(navController)
    }
}

private sealed class PremiumSettingsRoute {
    @Serializable
    data object Settings : PremiumSettingsRoute()

    @Serializable
    data object LoginLogging : PremiumSettingsRoute()

    @Serializable
    data object SendSmsNotification : PremiumSettingsRoute()

    @Serializable
    data object AuthorizationInfo : PremiumSettingsRoute()

    @Serializable
    data class AuthorizationDetail(val initialRecordId: Long) : PremiumSettingsRoute()
}

@Composable
private fun PremiumSettingsNavHost(fragmentNavController: NavController) {
    val navController = rememberNavController()
    val viewModel: PremiumSettingsViewModel = koinViewModel()

    NavHost(
        navController = navController,
        startDestination = PremiumSettingsRoute.Settings
    ) {
        composable<PremiumSettingsRoute.Settings> {
            PremiumSettingsScreen(
                uiState = viewModel.uiState,
                onCheckAddressContractClick = {
                    fragmentNavController.premiumAction {
                        viewModel.setAddressContractChecking(it)
                    }
                },
                onAmlCheckReceivedClick = {
                    fragmentNavController.premiumAction {
                        viewModel.setAmlCheckReceivedEnabled(it)
                    }
                },
                onLoginLoggingClick = {
                    fragmentNavController.authorizedLoggingAction {
                        navController.navigate(PremiumSettingsRoute.LoginLogging)
                    }
                },
                onAuthorizationInfoClick = {
                    fragmentNavController.authorizedLoggingAction {
                        navController.navigate(PremiumSettingsRoute.AuthorizationInfo)
                    }
                },
                onClose = fragmentNavController::popBackStack
            )
        }
        composablePage<PremiumSettingsRoute.LoginLogging> {
            val loggingSettingsViewModel: LoggingSettingsViewModel = koinViewModel()

            LoggingSettingsScreen(
                uiState = loggingSettingsViewModel.uiState,
                onLogSuccessfulLoginsToggle = fragmentNavController.ensurePinSetPremiumAction(
                    descriptionResId = R.string.pin_set_for_login_logging,
                    setter = loggingSettingsViewModel::setLogSuccessfulLoginsEnabled
                ),
                onSelfieOnSuccessfulLoginToggle = fragmentNavController.ensurePinSetPremiumAction(
                    descriptionResId = R.string.pin_set_for_login_logging,
                    setter = loggingSettingsViewModel::setSelfieOnSuccessfulLoginEnabled
                ),
                onLogUnsuccessfulLoginsToggle = fragmentNavController.ensurePinSetPremiumAction(
                    descriptionResId = R.string.pin_set_for_login_logging,
                    setter = loggingSettingsViewModel::setLogUnsuccessfulLoginsEnabled
                ),
                onSelfieOnUnsuccessfulLoginToggle = fragmentNavController.ensurePinSetPremiumAction(
                    descriptionResId = R.string.pin_set_for_login_logging,
                    setter = loggingSettingsViewModel::setSelfieOnUnsuccessfulLoginEnabled
                ),
                onLogIntoDuressModeToggle = fragmentNavController.ensurePinSetPremiumAction(
                    descriptionResId = R.string.pin_set_for_login_logging,
                    setter = loggingSettingsViewModel::setLogIntoDuressModeEnabled
                ),
                onSelfieOnDuressLoginToggle = fragmentNavController.ensurePinSetPremiumAction(
                    descriptionResId = R.string.pin_set_for_login_logging,
                    setter = loggingSettingsViewModel::onSelfieOnDuressLoginEnabled
                ),
                onPasscodeToggle = fragmentNavController.ensurePinSetPremiumAction(
                    descriptionResId = R.string.pin_set_for_login_logging_passcode,
                    pinType = PinType.LOG_LOGGING
                ) { enabled ->
                    if (!enabled) {
                        fragmentNavController.authorizedAction(
                            ConfirmPinFragment.InputConfirm(
                                descriptionResId = R.string.confirm_pin_to_disable_login_logging_passcode,
                                pinType = PinType.LOG_LOGGING
                            )
                        ) {
                            loggingSettingsViewModel.disablePasscodeLoggingPin()
                        }
                    }
                    loggingSettingsViewModel.updatePasscodeLoggingState()
                },
                onSendLoginNotificationClick = {
                    fragmentNavController.premiumAction {
                        fragmentNavController.ensurePinSet(R.string.pin_set_for_login_logging) {
                            navController.navigate(PremiumSettingsRoute.SendSmsNotification)
                        }
                    }
                },
                onDeleteAllAuthDataOnDuressToggle = fragmentNavController.ensurePinSetPremiumAction(
                    descriptionResId = R.string.pin_set_for_login_logging,
                    setter = loggingSettingsViewModel::setDeleteAllAuthDataOnDuressEnabled
                ),
                onAutoDeletePeriodChanged = loggingSettingsViewModel::setAutoDeletePeriod,
                onDeleteAllLogs = loggingSettingsViewModel::deleteAllLogs,
                onClose = navController::navigateUp
            )
        }
        composablePage<PremiumSettingsRoute.SendSmsNotification> {
            val smsViewModel: SendSmsNotificationViewModel = koinViewModel()

            SendSmsNotificationScreen(
                navController = fragmentNavController,
                uiState = smsViewModel.uiState,
                onAccountSelected = smsViewModel::onAccountSelected,
                onAddressChanged = smsViewModel::onAddressChanged,
                onMemoChanged = smsViewModel::onMemoChanged,
                onSaveClick = smsViewModel::onSaveClick,
                onTestSmsClick = smsViewModel::onTestSmsClick,
                onSaveSuccessShown = smsViewModel::onSaveSuccessShown,
                onTestResultShown = smsViewModel::onTestResultShown,
                onCancelTest = smsViewModel::cancelTestTransaction,
                onClose = navController::navigateUp
            )
        }
        composablePage<PremiumSettingsRoute.AuthorizationInfo> {
            val authInfoViewModel: LoggingListViewModel = koinViewModel()

            LoggingListScreen(
                loginRecords = authInfoViewModel.loginRecordsFlow.collectAsLazyPagingItems(),
                onDeleteAllClick = authInfoViewModel::deleteAllLogs,
                onDeleteClick = authInfoViewModel::deleteLog,
                onItemClick = { recordId ->
                    navController.navigate(PremiumSettingsRoute.AuthorizationDetail(recordId))
                },
                onClose = navController::navigateUp
            )
        }
        composablePage<PremiumSettingsRoute.AuthorizationDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<PremiumSettingsRoute.AuthorizationDetail>()
            val viewModel: LoggingDetailViewModel = koinViewModel {
                parametersOf(route.initialRecordId)
            }

            LoggingDetailScreen(
                viewModel = viewModel,
                onClose = navController::navigateUp
            )
        }
    }
}

@Composable
internal fun PremiumSettingsScreen(
    uiState: PremiumSettingsUiState,
    onCheckAddressContractClick: (Boolean) -> Unit,
    onAmlCheckReceivedClick: (Boolean) -> Unit,
    onLoginLoggingClick: () -> Unit,
    onAuthorizationInfoClick: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.premium_settings),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(R.string.settings_smart_contract_check),
                    checked = uiState.checkEnabled,
                    onCheckedChange = onCheckAddressContractClick
                )
            }
            InfoText(
                text = stringResource(R.string.settings_smart_contract_check_description),
            )
            VSpacer(12.dp)
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(R.string.alpha_aml_title),
                    checked = uiState.amlCheckReceivedEnabled,
                    onCheckedChange = onAmlCheckReceivedClick
                )
            }
            VSpacer(12.dp)
            CellUniversalLawrenceSection(
                listOf(
                    {
                        RowWithArrow(
                            text = stringResource(R.string.login_logging_title),
                            showAlert = uiState.showAlertIcon,
                            onClick = onLoginLoggingClick
                        )
                    },
                    {
                        RowWithArrow(
                            text = stringResource(R.string.authorization_information),
                            onClick = onAuthorizationInfoClick
                        )
                    }
                )
            )
        }
    }
}

@Preview
@Composable
private fun PremiumSettingsScreenPreview() {
    ComposeAppTheme {
        PremiumSettingsScreen(
            uiState = PremiumSettingsUiState(
                checkEnabled = true,
                amlCheckReceivedEnabled = false
            ),
            onCheckAddressContractClick = {},
            onAmlCheckReceivedClick = {},
            onLoginLoggingClick = {},
            onAuthorizationInfoClick = {},
            onClose = {}
        )
    }
}
