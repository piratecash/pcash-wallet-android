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
import cash.p.terminal.wallet.title
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

class PayCoreDataFieldServiceFee(
    private val fee: BigDecimal
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
                    text = formatPayCoreServiceFee(fee),
                    textAlign = TextAlign.End
                )
            }
        )
    }
}

internal fun formatPayCoreServiceFee(
    fee: BigDecimal
): String = "${fee.stripTrailingZeros().toPlainString()} USDT"

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
                    text = networkType.displayNetworkName,
                    textAlign = TextAlign.End
                )
            }
        )
    }
}

private val PayCoreTicker.displayNetworkName: String
    get() = blockchainType?.title ?: name

private val PayCoreTicker.blockchainType: BlockchainType?
    get() = when (this) {
        PayCoreTicker.USDT -> BlockchainType.Tron
        PayCoreTicker.USDT_ERC20 -> BlockchainType.Ethereum
        PayCoreTicker.USDT_SPL -> BlockchainType.Solana
        PayCoreTicker.RUB -> null
    }
