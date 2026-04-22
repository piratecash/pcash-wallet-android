package cash.p.terminal.featureStacking.ui.staking

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class StackingType(
    val value: String,
    val minStackingAmount: Int,
    val maturationHours: Int,
    val accrualIntervalHours: Int,
) : Parcelable {
    PCASH("PIRATE", 100, 8, 1),
    COSANTA("COSA", 1, 24, 12);

    val maxHoursUntilFirstAccrual: Int
        get() = maturationHours + accrualIntervalHours
}
