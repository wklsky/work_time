/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/mine/MineViewModel.kt
 * @Description: 我的页数据概览，汇总记录天数与累计正常/加班工时
 */
package com.worktime.pro.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.domain.repository.WorkTimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MineStats(
    val recordDays: Int = 0,
    val regularMinutes: Int = 0,
    val overtimeMinutes: Int = 0
)

class MineViewModel(
    private val repository: WorkTimeRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(MineStats())
    val stats: StateFlow<MineStats> = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            val sessions = repository.getAllSessions()
            val totals = sessions.toDayTotals()
            _stats.value = MineStats(
                recordDays = sessions.map { it.workDate }.distinct().count(),
                regularMinutes = totals.regularMinutes,
                overtimeMinutes = totals.overtimeMinutes
            )
        }
    }
}
