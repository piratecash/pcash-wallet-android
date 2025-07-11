package cash.p.terminal.modules.coin.tweets

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twitter.twittertext.Extractor
import cash.p.terminal.R
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.core.helpers.DateHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class CoinTweetsViewModel(
    private val service: CoinTweetsService,
    private val extractor: Extractor,
) : ViewModel() {
    val twitterPageUrl get() = "https://twitter.com/${service.username}"

    val isRefreshingLiveData = MutableLiveData(false)
    val itemsLiveData = MutableLiveData<List<TweetViewItem>>()
    val viewStateLiveData = MutableLiveData<ViewState>(ViewState.Loading)

    init {
        viewModelScope.launch {
            service.stateObservable.asFlow().collect { state ->
                isRefreshingLiveData.postValue(false)

                state.dataOrNull?.let {
                    itemsLiveData.postValue(it.map { getTweetViewItem(it) })
                }

                state.viewState?.let {
                    viewStateLiveData.postValue(it)
                }
            }
        }

        service.start()
    }

    private fun getTweetViewItem(tweet: Tweet) = TweetViewItem(
        title = tweet.user.name,
        subtitle = "@${tweet.user.username}",
        titleImageUrl = tweet.user.profileImageUrl,
        text = tweet.text,
        attachments = tweet.attachments,
        date = DateHelper.getDayAndTime(tweet.date),
        referencedTweet = tweet.referencedTweet?.let { referencedTweet ->
            val typeStringRes = when (referencedTweet.referenceType) {
                Tweet.ReferenceType.Quoted -> R.string.CoinPage_Twitter_Quoted
                Tweet.ReferenceType.Retweeted -> R.string.CoinPage_Twitter_Retweeted
                Tweet.ReferenceType.Replied -> R.string.CoinPage_Twitter_Replied
            }
            val title = TranslatableString.ResString(typeStringRes, "@${referencedTweet.tweet.user.username}")

            ReferencedTweetViewItem(
                title = title,
                text = referencedTweet.tweet.text
            )
        },
        entities = extractor.extractEntitiesWithIndices(tweet.text),
        url = "https://twitter.com/${tweet.user.username}/status/${tweet.id}"
    )

    fun refresh() {
        isRefreshingLiveData.postValue(true)
        service.refresh()
    }

    override fun onCleared() {
        service.stop()
    }
}

