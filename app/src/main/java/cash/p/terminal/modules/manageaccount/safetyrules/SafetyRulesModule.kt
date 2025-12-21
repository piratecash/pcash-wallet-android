package cash.p.terminal.modules.manageaccount.safetyrules

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object SafetyRulesModule {

    @Parcelize
    data class Input(
        val mode: SafetyRulesMode
    ) : Parcelable

    @Parcelize
    enum class SafetyRulesMode : Parcelable {
        AGREE,        // First time - checkboxes unchecked, "I Agree" button
        COPY_CONFIRM  // Copy confirmation - checkboxes pre-checked, "Risk It" + "Don't Copy" buttons
    }
}
