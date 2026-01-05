package io.horizontalsystems.core

import androidx.paging.PagingData
import io.horizontalsystems.core.entities.AutoDeletePeriod
import kotlinx.coroutines.flow.Flow

/**
 * Interface for login record repository operations.
 * Used by the feature-logging module.
 */
interface ILoginRecordRepository {
    companion object {
        const val PHOTOS_DIR = "login_photos"
    }

    /**
     * Get paged login records for a specific user level.
     */
    fun getPager(level: Int): Flow<PagingData<ILoginRecord>>

    /**
     * Observe whether there are any records for a specific user level.
     */
    fun observeHasRecords(level: Int): Flow<Boolean>

    /**
     * Delete all login records for a specific user level.
     */
    suspend fun deleteAll(level: Int)

    /**
     * Delete a single login record by ID.
     */
    suspend fun deleteById(id: Long)

    /**
     * Insert a new login record.
     */
    suspend fun insert(
        timestamp: Long,
        isSuccessful: Boolean,
        userLevel: Int,
        accountId: String,
        photoPath: String? = null
    ): Long

    /**
     * Delete expired login records based on the auto-delete period.
     */
    suspend fun deleteExpired(level: Int, period: AutoDeletePeriod)

    /**
     * Get all login records for a specific user level (non-paged).
     * Used for detail screen that needs full list access.
     */
    suspend fun getAll(level: Int): List<ILoginRecord>
}

/**
 * Interface representing a login record.
 */
interface ILoginRecord {
    val id: Long
    val accountId: String
    val timestamp: Long
    val isSuccessful: Boolean
    val userLevel: Int
    val photoPath: String?
}
