/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayViewModel.kt
 * @Description: 今日页状态管理，汇总当天打卡事件与工时段，提供快速打卡写入与删除
 */
package com.worktime.pro.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.repository.PunchInput
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.DateTicker
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class TodayState(
    val today: LocalDate = LocalDate.now(),
    val events: List<PunchEventEntity> = emptyList(),
    val sessions: List<WorkSessionEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: WorkTimeRepository,
    @Suppress("UNUSED_PARAMETER") settings: SettingsDataStore
) : ViewModel() {

    /** 跟随零点自动切换，避免应用整夜打开后仍停留在昨天 */
    val today: StateFlow<LocalDate> = DateTicker.dates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    private val dateKey: Flow<String> =
        today.map { TimeFormatter.formatDateKey(it) }.distinctUntilChanged()

    /** 实时计时：仅在今日页可见时每秒发射 */
    val now: StateFlow<LocalDateTime> = DateTicker.seconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    /**
     * 当前分钟：由每秒计时截断秒/纳秒后去重得到，仅每分钟变化一次。
     * 业务说明：快速打卡默认时间需随手机时间推进，若直接用每秒时间会在选择器里剧烈跳动，
     * 故只保留到分钟粒度；页面停留较久后也不会停留在进入页面那一刻的旧时间。
     */
    val nowMinute: StateFlow<LocalTime> = now
        .map { it.toLocalTime().withSecond(0).withNano(0) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LocalTime.now().withSecond(0).withNano(0)
        )

    /** 规则里的下班时间，用于状态卡展示“距下班” */
    val scheduledEnd: StateFlow<LocalTime> = repository.observeWorkRule()
        .map { rule ->
            rule?.endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: LocalTime.of(18, 0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalTime.of(18, 0))

    val state: StateFlow<TodayState> = dateKey.flatMapLatest { key ->
        combine(
            repository.observeEvents(key),
            repository.observeSessions(key)
        ) { events, sessions ->
            TodayState(
                today = TimeFormatter.parseDateKey(key),
                events = events,
                sessions = sessions
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun deleteEvent(event: PunchEventEntity) {
        viewModelScope.launch { repository.deletePunch(event) }
    }

    /**
     * 快速打卡：以用户选定的类型和自定义时间写入当天记录。
     * 业务说明：来源统一记为 MANUAL，与手动录入保持一致，便于统计时区分 OCR 识别与人工补录；
     * 写入后由 Repository 自动重算当日工时段，今日页与首页日历通过 Flow 自动刷新。
     */
    fun quickPunch(type: PunchType, time: LocalTime, onDone: () -> Unit) {
        viewModelScope.launch {
            // 以点击瞬间的日期为准，避免 ViewModel 构造时快照导致的跨零点写错日期
            val date = LocalDate.now()
            repository.addPunch(
                PunchInput(
                    workDate = TimeFormatter.formatDateKey(date),
                    punchType = type,
                    punchTime = LocalDateTime.of(date, time),
                    source = PunchSource.MANUAL
                )
            )
            onDone()
        }
    }

    fun formatTime(event: PunchEventEntity, use24Hour: Boolean = true): String =
        TimeFormatter.formatTime(
            java.time.Instant.ofEpochMilli(event.punchTime)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime(),
            use24Hour
        )
}
