package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.entities.UserDeletedWallet

@Dao
interface UserDeletedWalletDao {

    @Query("SELECT EXISTS(SELECT 1 FROM UserDeletedWallet WHERE accountId = :accountId AND tokenQueryId = :tokenQueryId)")
    suspend fun exists(accountId: String, tokenQueryId: String): Boolean

    @Query("SELECT tokenQueryId FROM UserDeletedWallet WHERE accountId = :accountId")
    suspend fun getDeletedTokenQueryIds(accountId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userDeletedWallet: UserDeletedWallet)

    @Query("DELETE FROM UserDeletedWallet WHERE accountId = :accountId AND tokenQueryId = :tokenQueryId")
    suspend fun delete(accountId: String, tokenQueryId: String)
}
