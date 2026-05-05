package cash.p.terminal.modules.multiswap.providers

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SwapProvidersRepository(
    private val preferences: SharedPreferences,
) {
    private val _disabledIds = MutableStateFlow(loadDisabledIds())
    val disabledIds: StateFlow<Set<String>> = _disabledIds.asStateFlow()

    fun isDisabled(providerId: String): Boolean =
        providerId in _disabledIds.value

    fun isMandatory(providerId: String): Boolean =
        providerId in MANDATORY_IDS

    fun disable(providerId: String) {
        if (isMandatory(providerId)) return
        mutateAndSave { it + providerId }
    }

    fun enable(providerId: String) {
        mutateAndSave { it - providerId }
    }

    fun setDisabled(providerId: String, disabled: Boolean) {
        if (disabled) disable(providerId) else enable(providerId)
    }

    private fun mutateAndSave(transform: (Set<String>) -> Set<String>) {
        val before = _disabledIds.value
        val after = transform(before)
        if (after == before) return
        _disabledIds.update { after }
        preferences.edit {
            putStringSet(KEY_DISABLED_SWAP_PROVIDERS, after)
        }
    }

    private fun loadDisabledIds(): Set<String> =
        preferences.getStringSet(KEY_DISABLED_SWAP_PROVIDERS, emptySet()).orEmpty()

    companion object {
        private const val KEY_DISABLED_SWAP_PROVIDERS = "disabled_swap_providers"
        val MANDATORY_IDS: Set<String> = setOf(PancakeSwapProvider.id, PancakeSwapV3Provider.id)
    }
}
