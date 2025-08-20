package cash.p.terminal.modules.xtransaction.sections

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.amount.AmountInputType
import cash.p.terminal.modules.fee.HSFeeRaw
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import io.horizontalsystems.core.entities.CurrencyValue

@Composable
fun FeeSection(
    transactionInfoHelper: TransactionInfoHelper,
    fee: TransactionValue.CoinValue,
    navController: NavController,
) {
    SectionUniversalLawrence {
        val rateCurrencyValue = transactionInfoHelper.getXRate(fee.coinUid)?.let {
            CurrencyValue(
                currency = transactionInfoHelper.getCurrency(),
                value = it
            )
        }
        HSFeeRaw(
            coinCode = fee.coinCode,
            coinDecimal = fee.decimals,
            fee = fee.value,
            amountInputType = AmountInputType.COIN,
            rate = rateCurrencyValue,
            navController = navController
        )
    }
}