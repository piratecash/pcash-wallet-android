package cash.p.terminal.modules.multiswap.providersettings

import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRegistry
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.modules.paycore.PayCoreFeatureToggle
import cash.p.terminal.modules.paycore.PayCoreProvider
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.launch

class SwapProvidersSettingsViewModel(
    private val registry: SwapProvidersRegistry,
    private val repository: SwapProvidersRepository,
    private val payCoreFeatureToggle: PayCoreFeatureToggle,
) : ViewModelUiState<SwapProvidersSettingsUiState>() {

    init {
        viewModelScope.launch {
            repository.disabledIds.collect { emitState() }
        }
    }

    override fun createState(): SwapProvidersSettingsUiState =
        SwapProvidersSettingsUiState(
            items = registry.providers
                .filter { payCoreFeatureToggle.isEnabled() || it.id != PayCoreProvider.ID }
                .sortedBy { it.title.lowercase() }
                .map { provider ->
                    SwapProviderItem(
                        id = provider.id,
                        title = provider.title,
                        icon = provider.icon,
                        enabled = !repository.isDisabled(provider.id),
                        mandatory = repository.isMandatory(provider.id),
                    )
                }
        )

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        repository.setDisabled(providerId, !enabled)
    }
}

data class SwapProvidersSettingsUiState(
    val items: List<SwapProviderItem>,
)

data class SwapProviderItem(
    val id: String,
    val title: String,
    val icon: Int,
    val enabled: Boolean,
    val mandatory: Boolean,
)
