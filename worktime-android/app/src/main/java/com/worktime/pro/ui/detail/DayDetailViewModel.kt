/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/detail/DayDetailViewModel.kt
 * @Description: 日详情状态管理，展示某日四类时间、工时段汇总与凭证，并支持编辑删除
 */
package com.worktime.pro.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.domain.repository.WorkTimeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayDetailState(
    val events: List<PunchEventEntity> = emptyList(),
    val sessions: List<WorkSessionEntity> = emptyList()
)

class DayDetailViewModel(
    private val dateKey: String,
    private val repository: WorkTimeRepository,
    @Suppress("UNUSED_PARAMETER") settings: SettingsDataStore
) : ViewModel() {

    val state: StateFlow<DayDetailState> = combine(
        repository.observeEvents(dateKey),
        repository.observeSessions(dateKey)
    ) { events, sessions -> DayDetailState(events, sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayDetailState())

    fun deleteEvent(event: PunchEventEntity) {
        viewModelScope.launch {
            repository.deletePunch(event)
        }
    }
}
