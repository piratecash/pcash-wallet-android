package cash.p.terminal.modules.settings.faq

import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.FaqManager
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.core.retryWhen
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.entities.FaqMap
import cash.p.terminal.entities.FaqSection
import io.horizontalsystems.core.DispatcherProvider
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FaqRepository(
    private val faqManager: FaqManager,
    private val connectivityManager: ConnectivityManager,
    private val languageManager: LanguageManager,
    dispatcherProvider: DispatcherProvider,
) {

    val faqList: Observable<DataState<List<FaqSection>>>
        get() = faqListSubject

    private val faqListSubject = BehaviorSubject.create<DataState<List<FaqSection>>>()
    private val coroutineScope = CoroutineScope(dispatcherProvider.io)
    private val retryLimit = 3

    fun start() {
        fetch()

        coroutineScope.launch {
            connectivityManager.networkAvailabilityFlow.collect {
                if (connectivityManager.isConnected.value && faqListSubject.value is DataState.Error) {
                    fetch()
                }
            }
        }
    }

    fun clear() {
        coroutineScope.cancel()
    }

    private fun fetch() {
        faqListSubject.onNext(DataState.Loading)

        coroutineScope.launch {
            try {
                val faqMaps = retryWhen(
                    times = retryLimit,
                    predicate = { it is AssertionError }
                ) {
                    faqManager.getFaqList()
                }

                val faqSections = getByLocalLanguage(
                    faqMaps,
                    languageManager.currentLocale.language,
                    languageManager.fallbackLocale.language
                )
                faqListSubject.onNext(DataState.Success(faqSections))
            } catch (e: Throwable) {
                faqListSubject.onNext(DataState.Error(e))
            }
        }
    }

    private fun getByLocalLanguage(
        faqMultiLanguage: List<FaqMap>,
        language: String,
        fallbackLanguage: String
    ) =
        faqMultiLanguage.map { sectionMultiLang ->
            val categoryTitle = sectionMultiLang.section[language]
                ?: sectionMultiLang.section[fallbackLanguage]
                ?: ""
            val sectionItems =
                sectionMultiLang.items.mapNotNull { it[language] ?: it[fallbackLanguage] }

            FaqSection(categoryTitle, sectionItems)
        }
}
