package cash.p.terminal.feature.logging.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.feature.logging.data.model.LoginRecord
import kotlinx.coroutines.flow.Flow

@Dao
internal interface LoginRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loginRecord: LoginRecord): Long

    @Query("SELECT * FROM LoginRecord WHERE userLevel >= :level ORDER BY timestamp DESC")
    fun pagingSource(level: Int): PagingSource<Int, LoginRecord>

    @Query("SELECT * FROM LoginRecord WHERE userLevel >= :level ORDER BY timestamp DESC")
    suspend fun getAll(level: Int): List<LoginRecord>

    @Query("SELECT * FROM LoginRecord WHERE id = :id")
    suspend fun getById(id: Long): LoginRecord?

    @Query("DELETE FROM LoginRecord WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM LoginRecord WHERE userLevel >= :level")
    suspend fun deleteAll(level: Int)

    @Query("SELECT photoPath FROM LoginRecord WHERE id = :id")
    suspend fun getPhotoPath(id: Long): String?

    @Query("SELECT photoPath FROM LoginRecord WHERE userLevel >= :level AND photoPath IS NOT NULL")
    suspend fun getAllPhotoPaths(level: Int): List<String>

    @Query("SELECT COUNT(*) FROM LoginRecord WHERE userLevel >= :level")
    fun observeCount(level: Int): Flow<Int>

    @Query("SELECT photoPath FROM LoginRecord WHERE userLevel >= :level AND timestamp < :beforeTimestamp AND photoPath IS NOT NULL")
    suspend fun getPhotoPathsOlderThan(level: Int, beforeTimestamp: Long): List<String>

    @Query("DELETE FROM LoginRecord WHERE userLevel >= :level AND timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(level: Int, beforeTimestamp: Long)
}
