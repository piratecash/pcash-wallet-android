package cash.p.terminal.modules.settings.addresschecker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import io.horizontalsystems.core.ViewModelUiState

class AddressCheckerFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        AddressCheckerScreen(
            onCheckAddressClick = {
                navController.slideFromRight(R.id.addressCheckFragment)
            },
            onClose = navController::navigateUp
        )
    }

}

@Composable
fun AddressCheckerScreen(
    onCheckAddressClick: () -> Unit,
    onClose: () -> Unit
) {
    val viewModel = viewModel<AddressCheckerViewModel>(factory = AddressCheckerModule.Factory())
    val uiState = viewModel.uiState
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.address_checker_title),
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
                    text = stringResource(R.string.SettingsAddressChecker_RecipientCheck),
                    checked = uiState.checkEnabled,
                    onCheckedChange = viewModel::toggleAddressChecking
                )
            }
            InfoText(
                text = stringResource(R.string.SettingsAddressChecker_CheckTheRecipientInfo),
            )
            VSpacer(12.dp)
            CellUniversalLawrenceSection(
                listOf({
                    RowUniversal(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = onCheckAddressClick
                    ) {
                        body_leah(
                            text = stringResource(R.string.SettingsAddressChecker_CheckAddress),
                            maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                        Image(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(id = R.drawable.ic_arrow_right),
                            contentDescription = null,
                        )
                    }
                })
            )
            VSpacer(32.dp)
        }
    }
}


@Preview
@Composable
fun Preview_AddressChecker() {
    ComposeAppTheme {
        AddressCheckerScreen({}, {})
    }
}

class AddressCheckerViewModel(
    private val localStorage: ILocalStorage,
) : ViewModelUiState<AddressCheckerUiState>() {
    private var checkEnabled = localStorage.recipientAddressBaseCheckEnabled

    override fun createState() = AddressCheckerUiState(
        checkEnabled = checkEnabled
    )

    fun toggleAddressChecking(enabled: Boolean) {
        localStorage.recipientAddressBaseCheckEnabled = enabled
        checkEnabled = enabled
        emitState()
    }
}

object AddressCheckerModule {
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddressCheckerViewModel(App.localStorage) as T
        }
    }
}

data class AddressCheckerUiState(
    val checkEnabled: Boolean
)