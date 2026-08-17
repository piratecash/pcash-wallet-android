package cash.p.terminal.modules.nft.asset

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.modules.balance.DefaultBalanceXRateRepository
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.IWalletManager
import kotlinx.parcelize.Parcelize
import org.koin.java.KoinJavaComponent.inject

object NftAssetModule {

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val collectionUid: String,
        private val nftUid: NftUid
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val service = NftAssetService(
                collectionUid,
                nftUid,
                App.accountManager,
                App.nftAdapterManager,
                App.nftMetadataManager.provider(nftUid.blockchainType),
                DefaultBalanceXRateRepository("nft-asset", App.currencyManager, App.marketKit)
            )
            val offlineOperationGate: OfflineOperationGate by inject(OfflineOperationGate::class.java)
            val walletManager: IWalletManager by inject(IWalletManager::class.java)

            return NftAssetViewModel(service, offlineOperationGate, walletManager) as T
        }
    }

    const val collectionUidKey = "collectionUidKey"
    const val nftUidKey = "nftUidKey"

    @Parcelize
    data class Input(val collectionUid: String?, val nftUidString: String) : Parcelable {
        val nftUid: NftUid
            get() = NftUid.fromUid(nftUidString)

        constructor(collectionUid: String?, nftUid: NftUid) : this(collectionUid, nftUid.uid)
    }

    enum class Tab(@StringRes val titleResId: Int) {
        Overview(R.string.NftAsset_Overview),
        Activity(R.string.NftAsset_Activity);
    }

    enum class NftAssetAction(@StringRes val title: Int) {
        Share(R.string.NftAsset_Action_Share),
        Save(R.string.NftAsset_Action_Save)
    }
}