package cash.p.terminal.modules.multiswap.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

interface ISwapSetting {
    val id: String
    @get:StringRes
    val titleRes: Int?
        get() = null

    @Composable
    fun GetContent(
        navController: NavController,
        onError: (Throwable?) -> Unit,
        onValueChange: (Any?) -> Unit
    )

    fun LazyListScope.addContentItems(
        navController: NavController,
        value: Any?,
        onError: (Throwable?) -> Unit,
        onValueChange: (Any?) -> Unit
    ) {
        item(key = id) {
            GetContent(
                navController = navController,
                onError = onError,
                onValueChange = onValueChange,
            )
        }
    }
}
