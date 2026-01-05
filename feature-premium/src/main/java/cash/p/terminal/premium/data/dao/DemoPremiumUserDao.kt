package cash.p.terminal.premium.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.premium.data.model.DemoPremiumUser

@Dao
internal interface DemoPremiumUserDao {
    @Query("SELECT * FROM demo_premium_users WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): DemoPremiumUser?

    @Query("DELETE FROM demo_premium_users WHERE address = :address")
    suspend fun deleteByAddress(address: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(premiumUser: DemoPremiumUser)

    @Query("SELECT EXISTS(SELECT 1 FROM demo_premium_users WHERE daysLeft > 0)")
    suspend fun hasActiveTrialPremium(): Boolean
}