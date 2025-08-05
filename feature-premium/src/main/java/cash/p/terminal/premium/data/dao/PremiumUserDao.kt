package cash.p.terminal.premium.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.premium.data.model.PremiumUser

@Dao
internal interface PremiumUserDao {
    @Query("SELECT * FROM premium_users WHERE level = :level")
    suspend fun getByLevel(level: Int): PremiumUser?

    @Query("DELETE FROM premium_users WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(premiumUser: PremiumUser)
} 