/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/detail/DayDetailViewModel.kt
 * @Description: 日详情状态管理，展示某日四类时间、工时段汇总与凭证，并支持编辑、新增与删除
 */
package com.worktime.pro.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.PunchHabit
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
import kotlinx.coroutines.flow.map
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
    settings: SettingsDataStore
) : ViewModel() {

    val state: StateFlow<DayDetailState> = combine(
        repository.observeEvents(dateKey),
        repository.observeSessions(dateKey)
    ) { events, sessions -> DayDetailState(events, sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayDetailState())

    /** 习惯打卡列表：在详情页一键写入当前查看的日期，而不是固定写今天 */
    val habits: StateFlow<List<PunchHabit>> = settings.settings
        .map { PunchHabit.deserialize(it.habits) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 应用习惯到当前详情页日期：把习惯里的固定时间批量写入 dateKey 对应的那一天。
     * overwrite=true 时先清空当日记录再写入，供"覆盖应用"确认场景使用。
     */
    fun applyHabit(habit: PunchHabit, overwrite: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.applyHabit(TimeFormatter.parseDateKey(dateKey), habit, overwrite)
            onResult(result.exceptionOrNull()?.message)
        }
    }

    /** 删除打卡：完整加班段会连同配对的另一端一起删除，返回值无需关心 */
    fun deleteEvent(event: PunchEventEntity) {
        viewModelScope.launch {
            repository.deletePunchGroup(event)
        }
    }

    /**
     * 整段修改一条加班：开始与结束一次性提交校验并重算。
     * 业务规则：分开改两端会产生"结束早于开始"的中间态非法时间线，必须合并提交。
     */
    fun updateOvertimeSegment(
        start: PunchEventEntity,
        end: PunchEventEntity,
        newStartTime: LocalTime,
        newEndTime: LocalTime,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val date = TimeFormatter.parseDateKey(dateKey)
            fun toMillis(time: LocalTime) = LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val result = repository.updateOvertimeSegment(
                start = start,
                end = end,
                newStartMillis = toMillis(newStartTime),
                newEndMillis = toMillis(newEndTime)
            )
            onResult(result.exceptionOrNull()?.message)
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
