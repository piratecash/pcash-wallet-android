package cash.p.terminal.modules.displayoptions

enum class DisplayDiffOptionType {
    NONE,
    PERCENT_CHANGE,
    PRICE_CHANGE,
    BOTH;

    val hasPriceChange: Boolean
        get() = this == PRICE_CHANGE || this == BOTH

    val hasPercentChange: Boolean
        get() = this == PERCENT_CHANGE || this == BOTH

    companion object {
        fun fromFlags(priceChange: Boolean, percentChange: Boolean): DisplayDiffOptionType {
            return when {
                priceChange && percentChange -> BOTH
                priceChange -> PRICE_CHANGE
                percentChange -> PERCENT_CHANGE
                else -> NONE
            }
        }
    }
}
