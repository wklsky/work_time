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
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.reminder.ReminderType
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

    /** 今日页两种打卡动作都可用时的默认动作，只接受上班/加班到 */
    fun setDefaultPunchType(type: PunchType) {
        viewModelScope.launch { settings.setDefaultPunchType(type.name) }
    }

    fun setGallerySaveEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setGallerySaveEnabled(enabled) }
    }

    /** 保存用户自定义的考勤机状态映射，传入序列化字符串 */
    fun setStatusMapping(value: String) {
        viewModelScope.launch { settings.setStatusMapping(value) }
    }

    /** 恢复默认状态映射：写入空串，识别时自动回退到内置编码 */
    fun resetStatusMapping() {
        viewModelScope.launch { settings.setStatusMapping("") }
    }

    fun setThemeMode(mode: com.worktime.pro.data.datastore.ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode.value) }
    }

    fun setTimeFormat(mode: com.worktime.pro.data.datastore.TimeFormatMode) {
        viewModelScope.launch { settings.setTimeFormat(mode.value) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setReminderEnabled(enabled) }
    }

    fun setReminderType(type: ReminderType) {
        viewModelScope.launch { settings.setReminderType(type.value) }
    }

    fun setReminderMinutes(minutes: Int) {
        viewModelScope.launch { settings.setReminderMinutes(minutes) }
    }
}
