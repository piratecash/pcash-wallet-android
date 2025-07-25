package cash.p.terminal.modules.market.posts

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.ui_compose.entities.DataState
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import cash.p.terminal.wallet.models.Post
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

class MarketPostService(
    private val marketKit: MarketKitWrapper,
    private val backgroundManager: BackgroundManager,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    private val stateSubject = BehaviorSubject.create<DataState<List<Post>>>()
    val stateObservable: Observable<DataState<List<Post>>>
        get() = stateSubject

    private fun fetchPosts() {
        job?.cancel()
        job = coroutineScope.launch {
            try {
                val posts = marketKit.postsSingle().await()
                stateSubject.onNext(DataState.Success(posts))
            } catch (e: Throwable) {
                stateSubject.onNext(DataState.Error(e))
            }
        }
    }

    fun start() {
        coroutineScope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    fetchPosts()
                }
            }
        }
    }

    fun stop() {
        coroutineScope.cancel()
    }

    fun refresh() {
        fetchPosts()
    }
}
