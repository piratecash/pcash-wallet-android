package cash.p.terminal.modules.xtransaction.helpers

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import cash.p.terminal.R
import coil3.compose.rememberAsyncImagePainter

@Composable
fun coinIconPainter(
    url: String?,
    alternativeUrl: String?,
    placeholder: Int?,
    fallback: Int = placeholder ?: R.drawable.coin_placeholder
) = rememberAsyncImagePainter(
    model = url,
    error = alternativeUrl?.let {
        rememberAsyncImagePainter(
            model = it,
            error = painterResource(fallback)
        )
    } ?: painterResource(fallback)
)