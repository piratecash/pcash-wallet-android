package cash.p.terminal.modules.market.overview.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah

@Composable
fun SeeAllButton(onClick: () -> Unit) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        body_leah(
            text = stringResource(R.string.Market_SeeAll),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(id = R.drawable.ic_arrow_right),
            contentDescription = "right arrow icon",
        )
    }
}
