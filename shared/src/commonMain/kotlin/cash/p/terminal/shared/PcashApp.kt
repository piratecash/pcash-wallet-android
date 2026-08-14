package cash.p.terminal.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.shared.main.MainDestinationTitle
import cash.p.terminal.shared.main.MainNavigation

@Composable
fun PcashApp(modifier: Modifier = Modifier) {
    MaterialTheme {
        var selectedDestination by remember { mutableStateOf(MainDestination.Balance) }
        MainNavigation(
            selectedDestination = selectedDestination,
            onDestinationSelect = { selectedDestination = it },
            modifier = modifier.fillMaxSize(),
        ) { destination ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(MainDestinationTitle(destination))
            }
        }
    }
}
