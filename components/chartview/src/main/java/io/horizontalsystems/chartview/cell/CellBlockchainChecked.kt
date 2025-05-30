package io.horizontalsystems.chartview.cell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import coil.compose.rememberAsyncImagePainter
import io.horizontalsystems.chartview.R
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl

@Composable
fun CellBlockchainChecked(
    borderTop: Boolean = true,
    blockchain: Blockchain,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    CellUniversal(
        borderTop = borderTop,
        onClick = onToggle
    ) {
        Image(
            painter = rememberAsyncImagePainterWithFallback(
                model = blockchain.type.imageUrl,
                error = painterResource(R.drawable.ic_platform_placeholder_32)
            ),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        body_leah(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            text = blockchain.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.ic_checkmark_20),
                tint = ComposeAppTheme.colors.jacob,
                contentDescription = null,
            )
        }
    }
}