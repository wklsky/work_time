/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeViewModel.kt
 * @Description: 首页状态管理，月区间一次聚合每日工时，提供显示模式与习惯打卡一键应用
 */
package com.worktime.pro.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.DayAggregate
import com.worktime.pro.data.db.DayPunchCounts
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.DateTicker
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class MonthDay(
    val date: LocalDate,
    val regularMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val recordCount: Int = 0,
    val isIncomplete: Boolean = false,
    val isToday: Boolean,
    val isCurrentMonth: Boolean,
    val isFuture: Boolean
)

private data class MonthData(
    val month: YearMonth,
    val aggregates: Map<String, DayAggregate>,
    val counts: Map<String, DayPunchCounts>
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: WorkTimeRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _displayedMonth = MutableStateFlow(YearMonth.now())
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth.asStateFlow()

    /** 全局显示模式：用户决策——设置中统一切换，影响所有日期格子 */
    val calendarDisplayMode: StateFlow<String> = settings.settings
        .map { it.calendarDisplayMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarDisplayMode.WORK_TIME.value)

    val showOvertimeShade: StateFlow<Boolean> = settings.settings
        .map { it.showOvertimeShade }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 当前月视图的 5/6 周区间聚合：一次查询覆盖整屏，格子只读内存数据 */
    private val monthData: Flow<MonthData> = _displayedMonth.flatMapLatest { month ->
        val start = gridStart(month)
        val end = start.plusDays((monthGridDays(month) - 1).toLong())
        val startKey = TimeFormatter.formatDateKey(start)
        val endKey = TimeFormatter.formatDateKey(end)
        combine(
            repository.observeDayAggregates(startKey, endKey),
            repository.observeDayPunchCounts(startKey, endKey)
        ) { aggregates, counts ->
            MonthData(
                month = month,
                aggregates = aggregates.associateBy { it.workDate },
                counts = counts.associateBy { it.workDate }
            )
        }
    }

    /** 月历数据：日期、每日工时、缺卡标记与今日高亮一起刷新 */
    val days: StateFlow<List<MonthDay>> = combine(DateTicker.dates(), monthData) { today, data ->
        buildMonthDays(data, today)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        buildMonthDays(MonthData(YearMonth.now(), emptyMap(), emptyMap()), LocalDate.now())
    )

    fun changeMonth(delta: Int) {
        _displayedMonth.value = _displayedMonth.value.plusMonths(delta.toLong())
    }

    fun goToToday() {
        _displayedMonth.value = YearMonth.now()
    }

    /** 年月快速跳转：直接定位到指定月份，供月份标题点击弹层使用 */
    fun goToMonth(month: YearMonth) {
        _displayedMonth.value = month
    }

    fun setCalendarDisplayMode(mode: String) {
        viewModelScope.launch {
            settings.setCalendarDisplayMode(mode)
        }
    }

    private fun buildMonthDays(data: MonthData, today: LocalDate): List<MonthDay> {
        val start = gridStart(data.month)
        return (0 until monthGridDays(data.month)).map { i ->
            val date = start.plusDays(i.toLong())
            val key = TimeFormatter.formatDateKey(date)
            val aggregate = data.aggregates[key]
            val counts = data.counts[key]
            val hasUnpaired = counts != null && (
                counts.workStarts != counts.workEnds ||
                    counts.otStarts != counts.otEnds
                )
            val hasEventsWithoutSessions = (counts?.eventCount ?: 0) > 0 && aggregate == null
            val isFuture = date.isAfter(today)
            MonthDay(
                date = date,
                regularMinutes = aggregate?.regularMinutes ?: 0,
                overtimeMinutes = aggregate?.overtimeMinutes ?: 0,
                recordCount = counts?.eventCount ?: 0,
                // 今天可能仍在上班，不把“只有一个上班”当成缺卡
                isIncomplete = !isFuture && date != today && (hasUnpaired || hasEventsWithoutSessions),
                isToday = date == today,
                isCurrentMonth = date.month == data.month.month && date.year == data.month.year,
                isFuture = isFuture
            )
        }
    }

    private fun gridStart(month: YearMonth): LocalDate {
        val firstDay = month.atDay(1)
        // 月历从周一开始，前置补齐上月末日期
        return firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
    }

    /**
     * 月视图所需格子数：从周一起补齐到整周，只渲染 5 行或 6 行。
     * 业务背景：固定 6 行时，5 行月份底部会多出一整排空白格子，挤压日历下方的图例与操作区。
     */
    private fun monthGridDays(month: YearMonth): Int {
        val leading = month.atDay(1).dayOfWeek.value - 1
        return ((leading + month.lengthOfMonth() + 6) / 7) * 7
    }

    fun dateKey(date: LocalDate): String = TimeFormatter.formatDateKey(date)
}
