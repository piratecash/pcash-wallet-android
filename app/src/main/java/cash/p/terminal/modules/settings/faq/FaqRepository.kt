package cash.p.terminal.modules.settings.faq

import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.FaqManager
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.core.retryWhen
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.entities.FaqMap
import cash.p.terminal.entities.FaqSection
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await

class FaqRepository(
    private val faqManager: FaqManager,
    private val connectivityManager: ConnectivityManager,
    private val languageManager: LanguageManager
) {

    val faqList: Observable<DataState<List<FaqSection>>>
        get() = faqListSubject

    private val faqListSubject = BehaviorSubject.create<DataState<List<FaqSection>>>()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val retryLimit = 3

    fun start() {
        fetch()

        coroutineScope.launch {
            connectivityManager.networkAvailabilitySignal.asFlow().collect {
                if (connectivityManager.isConnected && faqListSubject.value is DataState.Error) {
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
                    faqManager.getFaqList().await()
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
