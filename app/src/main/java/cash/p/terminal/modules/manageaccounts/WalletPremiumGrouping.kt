package cash.p.terminal.modules.manageaccounts

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.wallet.Account

/**
 * Wallet list split into the sections shown on the Manage Wallets and Switch Wallet screens.
 * Shared by both screens so the grouping rule lives in one place.
 */
data class WalletGroups(
    val premium: List<Account>,
    val other: List<Account>,
    val watch: List<Account>,
    val hardware: List<Account>,
)

/**
 * The premium type to show for this account. Watch accounts are never premium (enforced here so both
 * grouping and badges stay consistent for a single account).
 */
fun Account.resolvedPremiumType(premiumTypes: Map<String, PremiumType>): PremiumType =
    if (isWatchAccount) PremiumType.NONE else premiumTypes[id] ?: PremiumType.NONE

/**
 * Groups accounts by premium state. Watch accounts are never premium, so the remaining accounts fall
 * into watch / hardware / other. Each group is sorted by name.
 */
fun List<Account>.groupByPremium(premiumTypes: Map<String, PremiumType>): WalletGroups {
    val premium = mutableListOf<Account>()
    val other = mutableListOf<Account>()
    val watch = mutableListOf<Account>()
    val hardware = mutableListOf<Account>()
    for (account in sortedBy { it.name.lowercase() }) {
        when {
            account.resolvedPremiumType(premiumTypes).isPremium() -> premium.add(account)
            account.isWatchAccount -> watch.add(account)
            account.isHardwareWalletAccount -> hardware.add(account)
            else -> other.add(account)
        }
    }
    return WalletGroups(premium = premium, other = other, watch = watch, hardware = hardware)
}

@StringRes
fun PremiumType.badgeStringResOrNull(): Int? = when (this) {
    PremiumType.PIRATE -> R.string.manage_accounts_premium_badge_pirate
    PremiumType.COSA -> R.string.manage_accounts_premium_badge_cosa
    PremiumType.TRIAL -> R.string.manage_accounts_premium_badge_trial
    PremiumType.NONE -> null
}

@Composable
fun PremiumBadge(premiumType: PremiumType, modifier: Modifier = Modifier) {
    val badgeRes = premiumType.badgeStringResOrNull() ?: return
    subhead2_grey(text = stringResource(badgeRes), modifier = modifier)
}
