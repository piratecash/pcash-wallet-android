package cash.p.terminal.modules.send.address

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.core.address.ContractAddressChecker
import cash.p.terminal.core.premiumAction
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.wallet.Token

private val contractAddressChecker by lazy(LazyThreadSafetyMode.PUBLICATION) {
    ContractAddressChecker()
}

fun isSmartContractCheckSupported(token: Token): Boolean =
    contractAddressChecker.supports(token)

@Composable
fun SmartContractCheckSection(
    token: Token,
    navController: NavController,
    addressCheckerControl: AddressCheckerControl,
    modifier: Modifier = Modifier
) {
    val isSupported = remember(token.blockchainType, token.type) {
        isSmartContractCheckSupported(token)
    }

    if (!isSupported) return

    SectionUniversalLawrence(modifier = modifier) {
        SwitchWithText(
            text = stringResource(R.string.settings_smart_contract_check),
            checked = addressCheckerControl.uiState.addressCheckSmartContractEnabled,
            onCheckedChange = {
                navController.premiumAction {
                    addressCheckerControl.onCheckSmartContractAddressClick(it)
                }
            }
        )
    }
}
