package io.horizontalsystems.core.entities

import cash.p.terminal.strings.R
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle

enum class AutoDeletePeriod(val value: Int) : WithTranslatableTitle {
    NEVER(0),
    MONTH(1),
    YEAR(2);

    override val title: TranslatableString
        get() = TranslatableString.ResArrayString(R.array.login_logging_periods, ordinal)

    val shortTitle: TranslatableString
        get() = TranslatableString.ResArrayString(R.array.login_logging_periods_short, ordinal)

    companion object {
        fun fromValue(value: Int): AutoDeletePeriod =
            entries.find { it.value == value } ?: YEAR
    }
}
