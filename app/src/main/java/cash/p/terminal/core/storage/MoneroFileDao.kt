package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.entities.MoneroFileRecord

@Dao
interface MoneroFileDao {

    @Query("SELECT * FROM MoneroFileRecord WHERE accountId = :accountId")
    suspend fun getAssociatedRecord(accountId: String): MoneroFileRecord?

    @Query("DELETE FROM MoneroFileRecord WHERE accountId = :accountId")
    suspend fun deleteAssociatedRecord(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MoneroFileRecord)
}