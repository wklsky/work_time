/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsScreen.kt
 * @Description: 设置目录页，按分组展示入口与当前值摘要，点击进入对应子页
 */
package com.worktime.pro.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.ReminderMode
import com.worktime.pro.data.datastore.ThemeMode
import com.worktime.pro.data.datastore.TimeFormatMode
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.reminder.ReminderType
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.util.TimeFormatter

/**
 * 设置目录：只放入口，不放具体控件。
 * 目录行显示当前值摘要，用户不进子页也能确认现状，减少来回跳转。
 */
@Composable
fun SettingsScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit,
    onOpenPunch: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenData: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())

    // 目录摘要用的当前值：与各子页展示同一套数据，避免目录与子页文案打架
    val currentTheme = ThemeMode.entries.firstOrNull { it.value == settings.themeMode }
        ?: ThemeMode.SYSTEM
    val currentTimeFormat = TimeFormatMode.entries.firstOrNull { it.value == settings.timeFormat }
        ?: TimeFormatMode.SYSTEM
    val currentMode = CalendarDisplayMode.entries.firstOrNull { it.value == settings.calendarDisplayMode }
        ?: CalendarDisplayMode.WORK_TIME
    val currentDefaultPunch = PunchType.fromName(settings.defaultPunchType)
        ?: PunchType.WORK_START
    val currentReminderType = ReminderType.entries.firstOrNull { it.value == settings.reminderType }
        ?: ReminderType.OVERTIME
    val timeFormatLabel = when (currentTimeFormat) {
        TimeFormatMode.SYSTEM -> "跟随系统"
        TimeFormatMode.HOUR_24 -> "24 小时"
        TimeFormatMode.HOUR_12 -> "12 小时"
    }

    SettingsPage(
        title = "设置",
        subtitle = "全部设置仅保存在本机",
        onBack = onBack
    ) {
        SectionLabel("使用偏好")
        SettingsGroupCard {
            SettingsEntryRow(
                icon = Icons.Filled.TouchApp,
                title = "打卡设置",
                summary = "默认${currentDefaultPunch.label}",
                onClick = onOpenPunch
            )
            ItemDivider()
            SettingsEntryRow(
                icon = Icons.Filled.Palette,
                title = "显示与外观",
                summary = "${currentTheme.label} · $timeFormatLabel · ${currentMode.label}",
                onClick = onOpenDisplay
            )
            ItemDivider()
            SettingsEntryRow(
                icon = Icons.Filled.Notifications,
                title = "提醒",
                summary = if (settings.reminderEnabled) {
                    buildString {
                        append(currentReminderType.label)
                        append(" · ")
                        append(TimeFormatter.formatDuration(settings.reminderMinutes))
                        if (settings.reminderRingEnabled) {
                            append(" · ")
                            append(ReminderMode.fromValue(settings.reminderMode).label)
                        }
                    }
                } else {
                    "未开启"
                },
                onClick = onOpenReminder
            )
        }

        SectionLabel("数据管理")
        SettingsGroupCard {
            SettingsEntryRow(
                icon = Icons.Filled.Storage,
                title = "数据管理",
                summary = "仅本机 · JSON 备份 / CSV 明细",
                onClick = onOpenData
            )
        }
    }
}
