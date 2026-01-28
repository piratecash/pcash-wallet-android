package cash.p.terminal.network.pirate.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.network.pirate.data.database.entity.ChangeNowAssociationCoin

@Dao
internal interface CacheChangeNowCoinAssociationDao {
    @Query("SELECT * FROM change_now_coins WHERE uid = :coinGeckoUid")
    suspend fun getCoins(coinGeckoUid: String): ChangeNowAssociationCoin?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoins(entity: ChangeNowAssociationCoin)
}