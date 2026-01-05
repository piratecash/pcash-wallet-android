package cash.p.terminal.feature.logging.data.repository

import androidx.paging.PagingSource
import cash.p.terminal.feature.logging.data.dao.LoginRecordDao
import cash.p.terminal.feature.logging.data.model.LoginRecord
import kotlinx.coroutines.flow.Flow

internal class LoginRecordStorage(private val dao: LoginRecordDao) {

    suspend fun insert(loginRecord: LoginRecord): Long = dao.insert(loginRecord)

    fun pagingSource(level: Int): PagingSource<Int, LoginRecord> = dao.pagingSource(level)

    suspend fun getAll(level: Int): List<LoginRecord> = dao.getAll(level)

    suspend fun getById(id: Long): LoginRecord? = dao.getById(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll(level: Int) = dao.deleteAll(level)

    suspend fun getPhotoPath(id: Long): String? = dao.getPhotoPath(id)

    suspend fun getAllPhotoPaths(level: Int): List<String> = dao.getAllPhotoPaths(level)

    fun observeCount(level: Int): Flow<Int> = dao.observeCount(level)

    suspend fun getPhotoPathsOlderThan(level: Int, beforeTimestamp: Long): List<String> =
        dao.getPhotoPathsOlderThan(level, beforeTimestamp)

    suspend fun deleteOlderThan(level: Int, beforeTimestamp: Long) =
        dao.deleteOlderThan(level, beforeTimestamp)
}
