package cash.p.terminal.premium.domain

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal inline fun <reified T> getKoinInstance(): T {
    return object : KoinComponent {
        val value: T by inject()
    }.value
}