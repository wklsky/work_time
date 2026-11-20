/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryViewModel.kt
 * @Description: 统计汇总状态管理，按维度聚合区间工时并支持导出
 */
package com.worktime.pro.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.calculator.SummaryCalculator
import com.worktime.pro.domain.model.DailyMinutes
import com.worktime.pro.domain.model.SummaryRange
import com.worktime.pro.domain.model.SummaryResult
import com.worktime.pro.domain.repository.WorkTimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SummaryUiState(
    val range: SummaryRange = SummaryRange.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val customStart: LocalDate = LocalDate.now().minusDays(30),
    val customEnd: LocalDate = LocalDate.now(),
    val result: SummaryResult? = null,
    val rangeTruncated: Boolean = false,
    val isLoading: Boolean = false
)

class SummaryViewModel(
    private val repository: WorkTimeRepository,
    @Suppress("UNUSED_PARAMETER") settings: SettingsDataStore
) : ViewModel() {
    private val calculator = SummaryCalculator()

    private val _state = MutableStateFlow(SummaryUiState())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    init {
        recompute()
    }

    fun setRange(range: SummaryRange) {
        _state.value = _state.value.copy(range = range)
        recompute()
    }

    /** 区间翻页：按当前维度前后移动锚点，复用同一计算入口 */
    fun shiftAnchor(delta: Int) {
        val current = _state.value
        val newAnchor = when (current.range) {
            SummaryRange.DAY -> current.anchor.plusDays(delta.toLong())
            SummaryRange.WEEK -> current.anchor.plusWeeks(delta.toLong())
            SummaryRange.MONTH -> current.anchor.plusMonths(delta.toLong())
            SummaryRange.YEAR -> current.anchor.plusYears(delta.toLong())
            SummaryRange.CUSTOM -> current.anchor
        }
        _state.value = current.copy(anchor = newAnchor)
        recompute()
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        _state.value = _state.value.copy(customStart = start, customEnd = end)
        recompute()
    }

    /** 加载区间工时段并聚合为汇总结果 */
    fun recompute() {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(isLoading = true)
            try {
                val resolved = calculator.resolveRange(
                    current.range, current.anchor, current.customStart, current.customEnd
                )
                val sessions = repository.getSessionsInRange(resolved.start.toString(), resolved.end.toString())
                // 按工时段类型累加：REGULAR 段计工作分钟，全部段的加班分钟汇总
                val dailyMap = sessions.groupBy { it.workDate }.map { (date, list) ->
                    DailyMinutes(
                        date = date,
                        workMinutes = list.filter { it.sessionType == "REGULAR" }.sumOf { it.workMinutes },
                        overtimeMinutes = list.sumOf { it.overtimeMinutes }
                    )
                }
                val result = calculator.summarize(dailyMap, resolved.start, resolved.end, current.range)
                _state.value = _state.value.copy(
                    result = result,
                    rangeTruncated = resolved.truncated,
                    isLoading = false
                )
            } catch (e: Exception) {
                // 汇总失败不能让页面停留在 loading 态，需明确暴露错误
                _state.value = _state.value.copy(
                    result = null,
                    isLoading = false,
                    rangeTruncated = true
                )
            }
        }
    }
}
