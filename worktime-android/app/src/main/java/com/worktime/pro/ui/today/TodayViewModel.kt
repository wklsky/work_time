/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayViewModel.kt
 * @Description: 今日页状态管理，汇总当天打卡事件与工时段，提供快捷打卡入口数据
 */
package com.worktime.pro.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TodayState(
    val events: List<PunchEventEntity> = emptyList(),
    val sessions: List<WorkSessionEntity> = emptyList()
)

class TodayViewModel(
    private val repository: WorkTimeRepository,
    @Suppress("UNUSED_PARAMETER") settings: SettingsDataStore
) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    private val dateKey = TimeFormatter.formatDateKey(today)

    val state: StateFlow<TodayState> = combine(
        repository.observeEvents(dateKey),
        repository.observeSessions(dateKey)
    ) { events, sessions -> TodayState(events, sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())

    fun deleteEvent(event: PunchEventEntity) {
        viewModelScope.launch { repository.deletePunch(event) }
    }

    fun formatTime(event: PunchEventEntity): String =
        TimeFormatter.formatTime(
            java.time.Instant.ofEpochMilli(event.punchTime)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        )
}
