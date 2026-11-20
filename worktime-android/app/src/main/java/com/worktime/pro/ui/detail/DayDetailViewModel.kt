/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/detail/DayDetailViewModel.kt
 * @Description: 日详情状态管理，展示某日四类时间、工时段汇总与凭证，并支持编辑、新增与删除
 */
package com.worktime.pro.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.repository.PunchInput
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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

    /**
     * 修改某条打卡的时间：写入后由 Repository 自动重算当日工时段。
     * 业务说明：详情页编辑的是历史某一天，日期固定为详情页 dateKey，仅时间可变。
     */
    fun updateEventTime(
        event: PunchEventEntity,
        time: LocalTime,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val newMillis = LocalDateTime.of(TimeFormatter.parseDateKey(dateKey), time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val result = repository.updatePunch(event.copy(punchTime = newMillis))
            onResult(result.exceptionOrNull()?.message)
        }
    }

    /** 补录一段；与已有上班/加班段冲突时返回错误，不写入数据 */
    fun addEvent(type: PunchType, time: LocalTime, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.addPunch(
                PunchInput(
                    workDate = dateKey,
                    punchType = type,
                    punchTime = LocalDateTime.of(TimeFormatter.parseDateKey(dateKey), time),
                    source = PunchSource.MANUAL
                )
            )
            onResult(result.exceptionOrNull()?.message)
        }
    }
}
