package cash.p.terminal.modules.xtransaction.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.xtransaction.cells.AmountCellTV
import cash.p.terminal.modules.xtransaction.cells.AmountColor
import cash.p.terminal.modules.xtransaction.cells.AmountSign
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence

@Composable
fun MintSection(
    transactionValue: TransactionValue,
    transactionInfoHelper: TransactionInfoHelper,
    navController: NavController,
) {
    SectionUniversalLawrence {
        AmountCellTV(
            title = stringResource(R.string.Send_Confirmation_Mint),
            transactionValue = transactionValue,
            coinAmountColor = AmountColor.Positive,
            coinAmountSign = AmountSign.Plus,
            transactionInfoHelper = transactionInfoHelper,
            navController = navController,
            borderTop = false,
        )
    }
}