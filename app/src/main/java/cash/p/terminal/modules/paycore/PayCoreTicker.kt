package cash.p.terminal.modules.paycore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PayCoreTicker {
    @SerialName("RUB")
    RUB,

    @SerialName("USDT")
    USDT, // trc20

    @SerialName("USDT_ERC20")
    USDT_ERC20,

    @SerialName("USDT_SPL")
    USDT_SPL
}
