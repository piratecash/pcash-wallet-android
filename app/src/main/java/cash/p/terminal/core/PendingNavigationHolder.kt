package cash.p.terminal.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

interface PendingNavigationHolder<T : Any> {
    val pendingRoute: T?
    fun requestNavigation(route: T)
    fun onNavigationConsumed()
}

class PendingNavigationDelegate<T : Any> : PendingNavigationHolder<T> {
    override var pendingRoute: T? by mutableStateOf(null)
        private set

    override fun requestNavigation(route: T) {
        pendingRoute = route
    }

    override fun onNavigationConsumed() {
        pendingRoute = null
    }
}

@Composable
fun <T : Any> ObservePendingNavigation(
    holder: PendingNavigationHolder<T>,
    onNavigate: (T) -> Unit
) {
    val route = holder.pendingRoute
    LaunchedEffect(route) {
        route?.let {
            holder.onNavigationConsumed()
            onNavigate(it)
        }
    }
}
