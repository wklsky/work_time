/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsViewModel.kt
 * @Description: 设置页状态管理，管理日历显示模式、时间进制等偏好设置
 */
package com.worktime.pro.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.ClockDialStyle
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

    /** 今日页两种打卡动作都可用时的默认动作，只接受上班/加班到 */
    fun setDefaultPunchType(type: PunchType) {
        viewModelScope.launch { settings.setDefaultPunchType(type.name) }
    }

    fun setThemeMode(mode: com.worktime.pro.data.datastore.ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode.value) }
    }

    fun setTimeFormat(mode: com.worktime.pro.data.datastore.TimeFormatMode) {
        viewModelScope.launch { settings.setTimeFormat(mode.value) }
    }

    fun setClockDialStyle(style: ClockDialStyle) {
        viewModelScope.launch { settings.setClockDialStyle(style.value) }
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

    fun setReminderRingEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setReminderRingEnabled(enabled) }
    }

    fun setReminderRingtone(uri: String, name: String) {
        viewModelScope.launch { settings.setReminderRingtone(uri, name) }
    }

    fun clearReminderRingtone() {
        viewModelScope.launch { settings.clearReminderRingtone() }
    }
}
