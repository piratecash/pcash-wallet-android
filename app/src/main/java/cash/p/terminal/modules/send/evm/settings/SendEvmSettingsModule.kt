package cash.p.terminal.modules.send.evm.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.ethereum.CautionViewItemFactory
import cash.p.terminal.core.ethereum.EvmCoinService
import io.horizontalsystems.core.IAppNumberFormatter
import org.koin.java.KoinJavaComponent.inject

object SendEvmSettingsModule {

    class Factory(
        private val settingsService: SendEvmSettingsService,
        private val evmCoinService: EvmCoinService
    ) : ViewModelProvider.Factory {

        private val numberFormatter: IAppNumberFormatter by inject(IAppNumberFormatter::class.java)
        private val cautionViewItemFactory by lazy { CautionViewItemFactory(evmCoinService, numberFormatter) }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SendEvmSettingsViewModel(
                settingsService,
                cautionViewItemFactory
            ) as T
        }
    }
}
