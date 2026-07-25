package cash.p.terminal.modules.walletconnect.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WCPairingMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(metadata: WalletConnectV2PairingMetadata)

    @Query("SELECT * FROM WalletConnectV2PairingMetadata WHERE topic IN (:topics)")
    fun getByTopics(topics: List<String>): List<WalletConnectV2PairingMetadata>

    @Query("DELETE FROM WalletConnectV2PairingMetadata WHERE topic NOT IN (:topics)")
    fun deleteAllExcept(topics: List<String>)

}
