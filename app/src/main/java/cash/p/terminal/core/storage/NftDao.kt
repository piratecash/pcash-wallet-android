package cash.p.terminal.core.storage

import androidx.room.*
import cash.p.terminal.entities.nft.NftAssetRecord
import cash.p.terminal.entities.nft.NftCollectionRecord
import cash.p.terminal.entities.nft.NftMetadataSyncRecord
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.nft.*
import io.horizontalsystems.core.entities.BlockchainType

@Dao
interface NftDao {
    @Query("SELECT * FROM NftCollectionRecord WHERE blockchainType = :blockchainType AND accountId = :accountId")
    fun getCollections(blockchainType: BlockchainType, accountId: String): List<NftCollectionRecord>

    @Query("SELECT * FROM NftAssetRecord WHERE  blockchainType = :blockchainType AND accountId = :accountId")
    fun getAssets(blockchainType: BlockchainType, accountId: String): List<NftAssetRecord>

    @Query("SELECT * FROM NftAssetRecord WHERE nftUid = :nftUid")
    fun getAsset(nftUid: NftUid): NftAssetRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCollections(collectionRecords: List<NftCollectionRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAssets(assetRecords: List<NftAssetRecord>)

    @Query("DELETE FROM NftCollectionRecord WHERE blockchainType = :blockchainType AND accountId = :accountId ")
    fun deleteCollectionsForAccount(blockchainType: BlockchainType, accountId: String)

    @Query("DELETE FROM NftAssetRecord WHERE blockchainType = :blockchainType AND accountId = :accountId")
    fun deleteAssetsForAccount(blockchainType: BlockchainType, accountId: String)

    @Transaction
    fun replaceCollectionAssets(
        blockchainType: BlockchainType,
        accountId: String,
        collectionRecords: List<NftCollectionRecord>,
        assetRecords: List<NftAssetRecord>
    ) {
        deleteCollectionsForAccount(blockchainType, accountId)
        deleteAssetsForAccount(blockchainType, accountId)

        insertCollections(collectionRecords)
        insertAssets(assetRecords)
    }

    @Query("SELECT * FROM NftCollectionRecord WHERE accountId = :accountId AND uid = :slug")
    fun getCollection(accountId: String, slug: String): NftCollectionRecord?

    @Query("SELECT * FROM NftMetadataSyncRecord WHERE blockchainType = :blockchainType AND accountId = :accountId")
    fun getNftMetadataSyncRecord(blockchainType: BlockchainType, accountId: String): NftMetadataSyncRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNftMetadataSyncRecord(syncRecord: NftMetadataSyncRecord)

    @Query("SELECT * FROM NftAssetBriefMetadataRecord WHERE nftUid IN (:nftUids)")
    fun getNftAssetBriefMetadataRecords(nftUids: List<NftUid>): List<NftAssetBriefMetadataRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNftAssetBriefMetadataRecords(records: List<NftAssetBriefMetadataRecord>)
}

