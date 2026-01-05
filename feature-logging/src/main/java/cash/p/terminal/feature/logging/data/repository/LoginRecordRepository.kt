package cash.p.terminal.feature.logging.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import cash.p.terminal.feature.logging.data.model.LoginRecord
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ILoginRecord
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.entities.AutoDeletePeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

internal class LoginRecordRepository(
    private val context: Context,
    private val storage: LoginRecordStorage,
    private val dispatcherProvider: DispatcherProvider
) : ILoginRecordRepository {
    companion object {
        private const val PAGE_SIZE = 20
    }

    override suspend fun insert(
        timestamp: Long,
        isSuccessful: Boolean,
        userLevel: Int,
        accountId: String,
        photoPath: String?
    ): Long = withContext(dispatcherProvider.io) {
        val record = LoginRecord(
            timestamp = timestamp,
            isSuccessful = isSuccessful,
            userLevel = userLevel,
            accountId = accountId,
            photoPath = photoPath
        )
        storage.insert(record)
    }

    override fun getPager(level: Int): Flow<PagingData<ILoginRecord>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { storage.pagingSource(level) }
    ).flow.map { pagingData -> pagingData.map { it as ILoginRecord } }

    override fun observeHasRecords(level: Int): Flow<Boolean> =
        storage.observeCount(level).map { it > 0 }

    override suspend fun getAll(level: Int): List<ILoginRecord> = withContext(dispatcherProvider.io) {
        storage.getAll(level)
    }

    suspend fun getById(id: Long): ILoginRecord? = withContext(dispatcherProvider.io) {
        storage.getById(id)
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcherProvider.io) {
        val photoPath = storage.getPhotoPath(id)
        storage.deleteById(id)
        photoPath?.let { deletePhotoFile(it) }
    }

    override suspend fun deleteAll(level: Int) = withContext(dispatcherProvider.io) {
        val photoPaths = storage.getAllPhotoPaths(level)
        storage.deleteAll(level)
        photoPaths.forEach { deletePhotoFile(it) }
    }

    override suspend fun deleteExpired(level: Int, period: AutoDeletePeriod) = withContext(dispatcherProvider.io) {
        if (period == AutoDeletePeriod.NEVER) return@withContext

        val cutoffTimestamp = when (period) {
            AutoDeletePeriod.NEVER -> return@withContext
            AutoDeletePeriod.MONTH -> System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            AutoDeletePeriod.YEAR -> System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
        }

        val photoPaths = storage.getPhotoPathsOlderThan(level, cutoffTimestamp)
        storage.deleteOlderThan(level, cutoffTimestamp)
        photoPaths.forEach { deletePhotoFile(it) }
    }

    suspend fun cleanupOrphanedPhotos(level: Int) = withContext(dispatcherProvider.io) {
        val photosDir = File(context.filesDir, "${ILoginRecordRepository.PHOTOS_DIR}${File.separator}$level")
        if (!photosDir.exists()) return@withContext

        val validPaths = storage.getAllPhotoPaths(level).toSet()

        photosDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in validPaths) {
                file.delete()
            }
        }
    }

    private fun deletePhotoFile(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete photo at $path")
        }
    }
}
