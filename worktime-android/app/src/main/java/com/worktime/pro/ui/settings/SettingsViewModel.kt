/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsViewModel.kt
 * @Description: 设置页状态管理，管理日历显示模式、时间进制等偏好设置
 */
package com.worktime.pro.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.repository.WorkTimeRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsDataStore,
    @Suppress("UNUSED_PARAMETER") repository: WorkTimeRepository
) : ViewModel() {
    val settingsFlow = settings.settings

    fun setCalendarDisplayMode(mode: CalendarDisplayMode) {
        viewModelScope.launch { settings.setCalendarDisplayMode(mode.value) }
    }

    fun setOvertimeShade(enabled: Boolean) {
        viewModelScope.launch { settings.setOvertimeShade(enabled) }
    }

    fun setOcrAutoFill(enabled: Boolean) {
        viewModelScope.launch { settings.setOcrAutoFill(enabled) }
    }
}
