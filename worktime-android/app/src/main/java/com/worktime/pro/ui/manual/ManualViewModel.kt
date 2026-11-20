/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/manual/ManualViewModel.kt
 * @Description: 手动录入状态管理，供用户在无照片时选择类型与时间补录打卡
 */
package com.worktime.pro.ui.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.repository.PunchInput
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ManualViewModel(
    private val repository: WorkTimeRepository,
    @Suppress("UNUSED_PARAMETER") settings: SettingsDataStore
) : ViewModel() {

    /** 保存一条手动打卡，写入后由 Repository 自动重算工时 */
    fun save(date: LocalDate, type: PunchType, time: LocalTime, onDone: () -> Unit) {
        viewModelScope.launch {
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
}
