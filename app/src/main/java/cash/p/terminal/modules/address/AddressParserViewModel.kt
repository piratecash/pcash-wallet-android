package cash.p.terminal.modules.address

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.core.IAddressParser
import cash.p.terminal.core.utils.AddressUriResult
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.ui.compose.components.TextPreprocessor
import java.math.BigDecimal
import java.util.UUID

class AddressParserViewModel(
    private val parser: IAddressParser,
    prefilledData: PrefilledData?
) :
    ViewModel(), TextPreprocessor {
    private var lastEnteredText: String? = null

    var amountUnique by mutableStateOf(prefilledData?.amount?.let { AmountUnique(it) })
        private set
    var memoUnique by mutableStateOf(prefilledData?.memo?.let {
        MemoUnique(it, isNavigationPrefill = true)
    })
        private set

    val addressInputState: AddressInputState
        get() = AddressInputState(this, amountUnique, MemoPrefill(memoUnique, ::acknowledgeMemo))

    override fun process(text: String): String {
        var processed = text
        if (lastEnteredText.isNullOrBlank()) {
            // Full address validation is still handled in AddressViewModel.
            val addressData = parser.parse(text)
            val addressUri = (addressData as? AddressUriResult.Uri)?.addressUri
            processed = addressUri?.applyPrefill() ?: processed
        }

        lastEnteredText = text

        return processed
    }

    fun acknowledgeMemo(id: Long) {
        if (memoUnique?.id == id) memoUnique = null
    }

    private fun AddressUri.applyPrefill(): String? {
        val amount = amount
        val memo = value<String>(AddressUri.Field.Memo)
        if (amount == null && memo == null) return null

        amount?.let { amountUnique = AmountUnique(it) }
        memo?.let { memoUnique = MemoUnique(it) }
        return address
    }
}

data class AmountUnique(
    val amount: BigDecimal,
    val id: Long = UUID.randomUUID().leastSignificantBits
)

data class MemoUnique(
    val memo: String,
    val id: Long = UUID.randomUUID().leastSignificantBits,
    val isNavigationPrefill: Boolean = false,
)

data class MemoPrefill(
    val event: MemoUnique?,
    val handled: (Long) -> Unit,
)

data class AddressInputState(
    val textPreprocessor: TextPreprocessor,
    val amountUnique: AmountUnique?,
    val memoPrefill: MemoPrefill,
)
