package cash.p.terminal.modules.send.tron

import cash.p.terminal.entities.Address
import cash.p.terminal.modules.contacts.model.Contact
import io.horizontalsystems.marketkit.models.Coin
import java.math.BigDecimal

data class SendTronConfirmationData(
    val amount: BigDecimal,
    val address: Address,
    val fee: BigDecimal?,
    val activationFee: BigDecimal?,
    val resourcesConsumed: String?,
    val contact: Contact?,
    val coin: Coin,
    val feeCoin: Coin,
    val isInactiveAddress: Boolean,
    val memo: String? = null,
)
