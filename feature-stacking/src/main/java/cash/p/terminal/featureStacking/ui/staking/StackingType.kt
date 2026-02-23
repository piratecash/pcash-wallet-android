package cash.p.terminal.featureStacking.ui.staking

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class StackingType(val value: String, val minStackingAmount: Int) : Parcelable {
    PCASH("PIRATE", 100),
    COSANTA("COSA", 1)
}