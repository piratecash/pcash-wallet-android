package cash.p.terminal.premium.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.premium.data.model.AccountPremiumCacheEntity

@Dao
internal interface AccountPremiumCacheDao {
    @Query("SELECT * FROM account_premium_cache")
    suspend fun getAll(): List<AccountPremiumCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountPremiumCacheEntity)

    @Query("DELETE FROM account_premium_cache WHERE accountId IN (:accountIds)")
    suspend fun deleteByAccountIds(accountIds: List<String>)
}
