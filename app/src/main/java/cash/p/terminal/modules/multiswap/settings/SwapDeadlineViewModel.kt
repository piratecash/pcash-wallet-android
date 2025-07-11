package cash.p.terminal.modules.multiswap.settings

import android.util.Range
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.multiswap.settings.SwapSettingsModule.getState
import cash.p.terminal.modules.multiswap.settings.ui.InputButton
import io.reactivex.Observable
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import java.util.Optional
import kotlin.math.floor

interface ISwapDeadlineService {
    val initialDeadline: Long?
    val defaultDeadline: Long

    val deadlineError: Throwable?
    val deadlineErrorObservable: Observable<Optional<Throwable>>

    val recommendedDeadlineBounds: Range<Long>

    fun setDeadline(value: Long)
}

class SwapDeadlineViewModel(
    private val service: ISwapDeadlineService
) : ViewModel(), IVerifiedInputViewModel {

    var errorState by mutableStateOf<DataState.Error?>(null)
        private set

    override val inputButtons: List<InputButton>
        get() {
            val bounds = service.recommendedDeadlineBounds
            val lowerMinutes = toMinutes(bounds.lower)
            val upperMinutes = toMinutes(bounds.upper)

            return listOf(
                InputButton(
                    cash.p.terminal.strings.helpers.Translator.getString(R.string.SwapSettings_DeadlineMinute, lowerMinutes),
                    lowerMinutes
                ),
                InputButton(
                    cash.p.terminal.strings.helpers.Translator.getString(R.string.SwapSettings_DeadlineMinute, upperMinutes),
                    upperMinutes
                )
            )
        }

    override val inputFieldPlaceholder = toMinutes(service.defaultDeadline)

    override val initialValue: String?
        get() = service.initialDeadline?.let { toMinutes(it) }

    init {
        viewModelScope.launch {
            service.deadlineErrorObservable.asFlow().collect {
                sync()
            }
        }
        sync()
    }

    private fun sync() {
        val caution = service.deadlineError?.localizedMessage?.let { localizedMessage ->
            Caution(localizedMessage, Caution.Type.Error)
        }
        errorState = getState(caution)
    }

    override fun onChangeText(text: String?) {
        service.setDeadline(text?.toLongOrNull()?.times(60) ?: service.defaultDeadline)
    }

    override fun isValid(text: String?): Boolean {
        return text.isNullOrBlank() || text.toLongOrNull() != null
    }

    private fun toMinutes(seconds: Long): String {
        return floor(seconds / 60.0).toLong().toString()
    }
}

interface IVerifiedInputViewModel {
    val inputButtons: List<InputButton> get() = listOf()

    val initialValue: String? get() = null
    val inputFieldPlaceholder: String? get() = null

    fun onChangeText(text: String?) = Unit
    fun isValid(text: String?): Boolean = true
}
