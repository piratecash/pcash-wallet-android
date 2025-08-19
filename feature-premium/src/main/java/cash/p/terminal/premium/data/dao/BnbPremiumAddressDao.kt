package cash.p.terminal.premium.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.premium.data.model.BnbPremiumAddress

@Dao
internal interface BnbPremiumAddressDao {
    @Query("SELECT * FROM bnb_premium_address WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: String): BnbPremiumAddress?

    @Query("DELETE FROM bnb_premium_address WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Query("DELETE FROM bnb_premium_address WHERE accountId NOT IN (:keepAccountIds)")
    suspend fun deleteExceptAccountIds(keepAccountIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bnbPremiumAddress: BnbPremiumAddress)
} 