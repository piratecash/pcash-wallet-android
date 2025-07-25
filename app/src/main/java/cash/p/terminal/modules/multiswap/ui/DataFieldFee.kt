package cash.p.terminal.modules.multiswap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import io.horizontalsystems.core.slideFromBottom
import cash.p.terminal.modules.evmfee.FeeSettingsInfoDialog
import cash.p.terminal.modules.multiswap.QuoteInfoRow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah

@Composable
fun DataFieldFee(
    navController: NavController,
    primary: String,
    secondary: String,
) {
    QuoteInfoRow(
        title = {
            val title = stringResource(id = R.string.FeeSettings_NetworkFee)
            val infoText = stringResource(id = R.string.FeeSettings_NetworkFee_Info)

            subhead2_grey(text = title)

            Image(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable(
                        onClick = {
                            navController.slideFromBottom(
                                R.id.feeSettingsInfoDialog,
                                FeeSettingsInfoDialog.Input(title, infoText)
                            )
                        },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ),
                painter = painterResource(id = R.drawable.ic_info_20),
                contentDescription = ""
            )

        },
        value = {
            Column(horizontalAlignment = Alignment.End) {
                subhead2_leah(text = primary)
                VSpacer(height = 1.dp)
                subhead2_grey(text = secondary)
            }
        }
    )
}