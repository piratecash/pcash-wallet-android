package cash.p.terminal.modules.displayoptions

import cash.p.terminal.R
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle

enum class DisplayPricePeriod(val key: String): WithTranslatableTitle {
    ONE_HOUR("1h"),
    ONE_DAY("1d"),
    ONE_WEEK("1w"),
    ONE_MONTH("1m"),
    ONE_YEAR("1y"),
    ALL("all");

    override val title: TranslatableString
        get() = TranslatableString.ResString(when(this) {
            ONE_HOUR -> R.string.display_options_period_1h
            ONE_DAY -> R.string.display_options_period_1d
            ONE_WEEK -> R.string.display_options_period_1w
            ONE_MONTH -> R.string.display_options_period_1m
            ONE_YEAR -> R.string.display_options_period_1y
            ALL -> R.string.display_options_period_all
        })

    val shortForm: TranslatableString
        get() = TranslatableString.ResString(when(this) {
            ONE_HOUR -> R.string.CoinPage_TimeDuration_Hour
            ONE_DAY -> R.string.CoinPage_TimeDuration_Day
            ONE_WEEK -> R.string.CoinPage_TimeDuration_Week
            ONE_MONTH -> R.string.CoinPage_TimeDuration_Month
            ONE_YEAR -> R.string.CoinPage_TimeDuration_Year
            ALL -> R.string.CoinPage_TimeDuration_All
        })
}