package cash.p.terminal.premium.domain

import androidx.navigation.NavController
import cash.p.terminal.premium.R
import cash.p.terminal.ui_compose.slideFromBottom
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

fun NavController.paidAction(block: () -> PremiumResult) {
    if (block() != PremiumResult.Success) {
        slideFromBottom(R.id.aboutPremiumFragment)
    }
}

internal inline fun <reified T> getKoinInstance(): T {
    return object : KoinComponent {
        val value: T by inject()
    }.value
}