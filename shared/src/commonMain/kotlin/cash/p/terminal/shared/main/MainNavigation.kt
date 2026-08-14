package cash.p.terminal.shared.main

import androidx.compose.material3.Icon
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.resources.Balance_Title
import cash.p.terminal.resources.Market_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_Title
import cash.p.terminal.resources.Transactions_Title
import cash.p.terminal.resources.ic_market_24
import cash.p.terminal.resources.ic_settings
import cash.p.terminal.resources.ic_transactions
import cash.p.terminal.resources.ic_wallet_24

private val MainDestination.resources: MainDestinationResources
    get() = when (this) {
        MainDestination.Balance -> MainDestinationResources(Res.string.Balance_Title, Res.drawable.ic_wallet_24)
        MainDestination.Transactions -> MainDestinationResources(Res.string.Transactions_Title, Res.drawable.ic_transactions)
        MainDestination.Market -> MainDestinationResources(Res.string.Market_Title, Res.drawable.ic_market_24)
        MainDestination.Settings -> MainDestinationResources(Res.string.Settings_Title, Res.drawable.ic_settings)
    }

private data class MainDestinationResources(
    val title: StringResource,
    val icon: DrawableResource,
)

@Composable
fun MainDestinationTitle(destination: MainDestination): String = stringResource(destination.resources.title)

@Composable
fun MainDestinationIcon(
    destination: MainDestination,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(destination.resources.icon),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
fun MainNavigation(
    selectedDestination: MainDestination,
    onDestinationSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (MainDestination) -> Unit = {},
) {
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            MainDestination.entries.forEach { destination ->
                item(
                    selected = destination == selectedDestination,
                    onClick = { onDestinationSelect(destination) },
                    icon = { MainDestinationIcon(destination, null) }
                )
            }
        },
    ) {
        content(selectedDestination)
    }
}
