package cash.p.terminal.modules.settings.faq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.FaqManager

object FaqModule {

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val faqRepository = FaqRepository(
                faqManager = FaqManager,
                connectivityManager = getKoinInstance(),
                languageManager = getKoinInstance(),
                dispatcherProvider = getKoinInstance(),
            )

            return FaqViewModel(faqRepository) as T
        }
    }
}
