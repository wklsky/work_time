/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeViewModel.kt
 * @Description: 首页状态管理，提供月历数据、每日汇总与全局显示模式设置
 */
package com.worktime.pro.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.model.DaySummary
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class MonthDay(
    val date: LocalDate,
    val summary: DaySummary?,
    val isToday: Boolean,
    val isCurrentMonth: Boolean
)

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

    /** 月历骨架数据：仅日期与归属标记，每日汇总由格子 composable 独立订阅，避免整月同步加载 */
    val days: StateFlow<List<MonthDay>> = _displayedMonth
        .map { buildMonthDays(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), buildMonthDays(YearMonth.now()))

    fun changeMonth(delta: Int) {
        _displayedMonth.value = _displayedMonth.value.plusMonths(delta.toLong())
    }

    fun goToToday() {
        _displayedMonth.value = YearMonth.now()
    }

    fun setCalendarDisplayMode(mode: String) {
        viewModelScope.launch {
            settings.setCalendarDisplayMode(mode)
        }
    }

    /**
     * 同步构建月历数据（不含实时汇总）。
     * 汇总数据的实时刷新由各格子单独订阅 observeDaySummary 完成，避免首页一次性加载整月时阻塞。
     */
    private fun buildMonthDays(month: YearMonth): List<MonthDay> {
        val today = LocalDate.now()
        val firstDay = month.atDay(1)
        // 月历从周一开始，前置补齐上月末日期
        val offset = firstDay.dayOfWeek.value - 1
        val startDate = firstDay.minusDays(offset.toLong())
        val cells = 42 // 6 行 × 7 列，保证任何月份都完整显示
        return (0 until cells).map { i ->
            val date = startDate.plusDays(i.toLong())
            MonthDay(
                date = date,
                summary = null,
                isToday = date == today,
                isCurrentMonth = date.month == month.month && date.year == month.year
            )
        }
    }

    fun dateKey(date: LocalDate): String = TimeFormatter.formatDateKey(date)
}
