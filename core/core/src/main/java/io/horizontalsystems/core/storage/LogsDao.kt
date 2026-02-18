package io.horizontalsystems.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LogsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(logEntry: LogEntry)

    @Query("SELECT * FROM LogEntry ORDER BY id")
    fun getAll(): List<LogEntry>

    @Query("SELECT * FROM LogEntry WHERE actionId LIKE '%' || :tag || '%' ORDER BY id")
    fun getByTag(tag: String): List<LogEntry>

    @Query("SELECT * FROM LogEntry WHERE actionId LIKE '%' || :tag || '%' ORDER BY id DESC LIMIT :limit")
    fun getRecentByTag(tag: String, limit: Int): List<LogEntry>

    @Query("DELETE FROM LogEntry WHERE date < :timestamp")
    fun deleteOlderThan(timestamp: Long)

}