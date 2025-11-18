package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.entities.ZcashSingleUseAddress

@Dao
interface ZcashSingleUseAddressDao {

    @Query(
        """
        SELECT * FROM ZcashSingleUseAddress
        WHERE accountId = :accountId
        AND hadBalance = 0
        ORDER BY useCount ASC
        LIMIT 1
        """
    )
    suspend fun getNextUnusedAddress(accountId: String): ZcashSingleUseAddress?

    @Query(
        """
        SELECT * FROM ZcashSingleUseAddress
        WHERE accountId = :accountId
        AND hadBalance = 0
        """
    )
    suspend fun getAddressesWithoutBalance(accountId: String): List<ZcashSingleUseAddress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(address: ZcashSingleUseAddress)

    @Query(
        """
        UPDATE ZcashSingleUseAddress
        SET useCount = useCount + 1
        WHERE accountId = :accountId AND address = :address
        """
    )
    suspend fun incrementUseCount(accountId: String, address: String)

    @Query(
        """
        UPDATE ZcashSingleUseAddress
        SET hadBalance = :hadBalance
        WHERE accountId = :accountId AND address = :address
        """
    )
    suspend fun updateHadBalance(accountId: String, address: String, hadBalance: Boolean)

    @Query("DELETE FROM ZcashSingleUseAddress WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)
}
