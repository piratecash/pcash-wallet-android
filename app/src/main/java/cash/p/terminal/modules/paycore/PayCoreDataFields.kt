package cash.p.terminal.modules.paycore

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import java.math.BigDecimal

class PayCoreDataFieldServiceFee(
    private val fee: BigDecimal,
    private val networkType: PayCoreTicker
) : DataField {
    @Composable
    override fun GetContent(navController: NavController, borderTop: Boolean) {
        QuoteInfoRow(
            borderTop = borderTop,
            title = {
                subhead2_grey(text = stringResource(R.string.paycore_service_fee))
            },
            value = {
                subhead2_leah(
                    text = formatPayCoreServiceFee(fee, networkType),
                    textAlign = TextAlign.End
                )
            }
        )
    }
}

internal fun formatPayCoreServiceFee(
    fee: BigDecimal,
    networkType: PayCoreTicker? = null,
): String {
    val networkSuffix = networkType?.let { " ($it)" }.orEmpty()
    return "${fee.stripTrailingZeros().toPlainString()} USDT$networkSuffix"
}

class PayCoreDataFieldNetwork(
    private val networkType: PayCoreTicker
) : DataField {
    @Composable
    override fun GetContent(navController: NavController, borderTop: Boolean) {
        QuoteInfoRow(
            borderTop = borderTop,
            title = {
                subhead2_grey(text = stringResource(R.string.paycore_network))
            },
            value = {
                subhead2_leah(
                    text = networkType.name,
                    textAlign = TextAlign.End
                )
            }
        )
    }
}
