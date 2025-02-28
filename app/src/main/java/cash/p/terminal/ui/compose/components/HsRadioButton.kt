package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.HsIconButton

@Composable
fun HsRadioButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val painterResource = if (selected) {
        painterResource(id = R.drawable.radio_on_24)
    } else {
        painterResource(id = R.drawable.radio_off_24)
    }

    HsIconButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Image(
            painter = painterResource,
            contentDescription = null
        )
    }

}
