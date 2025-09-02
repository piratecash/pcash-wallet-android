package cash.p.terminal.modules.walletconnect.request

import cash.p.terminal.modules.sendevmtransaction.SectionViewItem


data class WCActionState(
    val runnable: Boolean,
    val items: List<SectionViewItem>
)
