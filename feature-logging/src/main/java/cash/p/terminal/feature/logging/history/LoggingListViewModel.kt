package cash.p.terminal.feature.logging.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.ILoginRecord
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.helpers.DateHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

class LoggingListViewModel(
    private val loginRecordRepository: ILoginRecordRepository,
    private val accountManager: IAccountManager,
    private val userManager: UserManager
) : ViewModel() {

    val loginRecordsFlow: Flow<PagingData<LoginRecordViewItem>> =
        loginRecordRepository.getPager(userManager.getUserLevel())
            .map { pagingData ->
                pagingData.map { record -> mapToViewItem(record) }
            }
            .cachedIn(viewModelScope)

    fun deleteAllLogs() {
        viewModelScope.launch {
            loginRecordRepository.deleteAll(userManager.getUserLevel())
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            loginRecordRepository.deleteById(id)
        }
    }

    private fun mapToViewItem(record: ILoginRecord): LoginRecordViewItem {
        val account = accountManager.account(record.accountId)
        val date = Date(record.timestamp)

        return LoginRecordViewItem(
            id = record.id,
            photoPath = record.photoPath,
            isSuccessful = record.isSuccessful,
            isDuressMode = record.userLevel > 0,
            walletName = account?.name ?: "Unknown",
            formattedTime = formatDateTime(date),
            relativeTime = DateHelper.formatRelativeTime(record.timestamp)
        )
    }

    private fun formatDateTime(date: Date): String {
        val time = DateHelper.getOnlyTime(date)
        val dateStr = DateHelper.formatDate(date, "dd.MM.yyyy")
        return "$time $dateStr"
    }
}
