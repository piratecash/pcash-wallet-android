package cash.p.terminal.feature.logging.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.feature.logging.history.LoginRecordViewItem
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.ILoginRecord
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.helpers.DateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

class LoggingDetailViewModel(
    private val initialRecordId: Long,
    private val loginRecordRepository: ILoginRecordRepository,
    private val accountManager: IAccountManager,
    private val userManager: UserManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoggingDetailUiState(selectedId = initialRecordId))
    val uiState: StateFlow<LoggingDetailUiState> = _uiState.asStateFlow()

    init {
        loadRecords()
    }

    private fun loadRecords() {
        viewModelScope.launch {
            val records = loginRecordRepository.getAll(userManager.getUserLevel())
                .map { mapToViewItem(it) }

            _uiState.update {
                it.copy(
                    records = records,
                    selectedId = initialRecordId
                )
            }
        }
    }

    fun selectItem(selectedId: Long) {
        _uiState.update { it.copy(selectedId = selectedId) }
    }

    fun deleteCurrentRecord() {
        viewModelScope.launch {
            val currentRecord = _uiState.value.currentRecord ?: return@launch
            val records = _uiState.value.records
            val currentIndex = records.indexOfFirst { it.id == currentRecord.id }

            loginRecordRepository.deleteById(currentRecord.id)

            val newRecords = records.filterNot { it.id == currentRecord.id }

            if (newRecords.isEmpty()) {
                _uiState.update { it.copy(closeScreen = true) }
            } else {
                // Select next item (or previous if deleted last)
                val newIndex = minOf(currentIndex, newRecords.size - 1)
                val newSelectedId = newRecords[newIndex].id
                _uiState.update {
                    it.copy(
                        records = newRecords,
                        selectedId = newSelectedId
                    )
                }
            }
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
