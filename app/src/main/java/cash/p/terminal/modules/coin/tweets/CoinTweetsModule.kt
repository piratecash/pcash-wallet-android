package cash.p.terminal.modules.coin.tweets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.twitter.twittertext.Extractor
import cash.p.terminal.core.App
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.entities.FullCoin

object CoinTweetsModule {
    class Factory(private val fullCoin: FullCoin) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val service = CoinTweetsService(fullCoin.coin.uid, TweetsProvider(App.appConfigProvider.twitterBearerToken), App.marketKit)

            return CoinTweetsViewModel(service, Extractor()) as T
        }
    }
}

data class TweetViewItem(
    val title: String,
    val subtitle: String,
    val titleImageUrl: String,
    val text: String,
    val attachments: List<Tweet.Attachment>,
    val date: String,
    val referencedTweet: ReferencedTweetViewItem?,
    val entities: List<Extractor.Entity>,
    val url: String,
)

data class ReferencedTweetViewItem(val title: TranslatableString, val text: String)