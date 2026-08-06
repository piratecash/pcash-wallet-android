package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import cash.p.terminal.entities.AddressLabel
import cash.p.terminal.entities.AddressLabelSource

@Dao
interface AddressLabelDao {

    @Query("SELECT * FROM AddressLabel")
    suspend fun getAll(): List<AddressLabel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(labels: List<AddressLabel>)

    @Query("DELETE FROM AddressLabel WHERE source = :source")
    suspend fun deleteBySource(source: AddressLabelSource)

    @Transaction
    suspend fun replaceAndGetAll(
        source: AddressLabelSource,
        labels: List<AddressLabel>,
    ): List<AddressLabel> {
        deleteBySource(source)
        insert(labels)
        return getAll()
    }
}
