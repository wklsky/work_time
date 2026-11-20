/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/data/datastore/SettingsDataStore.kt
 * @Description: 本地设置存储，管理日历显示模式、时间进制、规则等偏好，替代旧版云端 user_setting 表
 */
package com.worktime.pro.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.worktime.pro.domain.reminder.ReminderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** 日历每日显示内容：全部 / 仅上班时长 / 仅加班时长，全局统一生效 */
enum class CalendarDisplayMode(val value: String, val label: String) {
    ALL("ALL", "全部"),
    WORK_TIME("WORK_TIME", "上班时长"),
    OVERTIME("OVERTIME", "加班时长")
}

enum class TimeFormatMode(val value: String) {
    SYSTEM("SYSTEM"),
    HOUR_24("24H"),
    HOUR_12("12H")
}

/** 主题模式：跟随系统 / 浅色 / 深色 / 黑白（纯灰阶高对比） */
enum class ThemeMode(val value: String, val label: String) {
    SYSTEM("SYSTEM", "跟随系统"),
    LIGHT("LIGHT", "浅色"),
    DARK("DARK", "深色"),
    BLACK_WHITE("BLACK_WHITE", "黑白")
}

data class AppSettings(
    val calendarDisplayMode: String = CalendarDisplayMode.WORK_TIME.value,
    /** 默认 24 小时制，符合工时记录场景对精确时间的需求 */
    val timeFormat: String = TimeFormatMode.HOUR_24.value,
    val showOvertimeShade: Boolean = true,
    val defaultInputMode: String = "OCR",
    val ocrAutoFill: Boolean = true,
    /** 确认保存后是否同时写入系统相册（备份永远不含照片） */
    val gallerySaveEnabled: Boolean = true,
    /** 用户自定义的考勤机状态映射，序列化字符串；空串表示使用默认映射 */
    val statusMapping: String = "",
    /** 默认白天（浅色）模式，界面更明亮清晰 */
    val themeMode: String = ThemeMode.LIGHT.value,
    /** 习惯打卡列表，序列化字符串；空串表示无习惯 */
    val habits: String = "",
    /** 启用用户自定义配色（黑白模式不受影响） */
    val customThemeEnabled: Boolean = false,
    /** 浅色/深色自定义配色，序列化 ARGB 列表；空串表示使用预设 */
    val customLightColors: String = "",
    val customDarkColors: String = "",
    /** 字号缩放：0.9 / 1.0 / 1.15 */
    val fontScale: Float = 1f,
    /** 累计工时提醒：达到类型阈值后发送系统通知 */
    val reminderEnabled: Boolean = false,
    val reminderType: String = ReminderType.OVERTIME.value,
    val reminderMinutes: Int = 420,
    /** 最近一次已提醒的标记，避免同一天重复通知 */
    val lastReminderKey: String = ""
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val CALENDAR_DISPLAY = stringPreferencesKey("calendar_display_mode")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val OVERTIME_SHADE = booleanPreferencesKey("show_overtime_shade")
        val DEFAULT_INPUT = stringPreferencesKey("default_input_mode")
        val OCR_AUTO_FILL = booleanPreferencesKey("ocr_auto_fill")
        val GALLERY_SAVE = booleanPreferencesKey("gallery_save_enabled")
        val STATUS_MAPPING = stringPreferencesKey("status_mapping")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HABITS = stringPreferencesKey("punch_habits")
        val CUSTOM_THEME = booleanPreferencesKey("custom_theme_enabled")
        val CUSTOM_LIGHT = stringPreferencesKey("custom_light_colors")
        val CUSTOM_DARK = stringPreferencesKey("custom_dark_colors")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_TYPE = stringPreferencesKey("reminder_type")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val LAST_REMINDER_KEY = stringPreferencesKey("last_reminder_key")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            calendarDisplayMode = prefs[Keys.CALENDAR_DISPLAY] ?: CalendarDisplayMode.WORK_TIME.value,
            timeFormat = prefs[Keys.TIME_FORMAT] ?: TimeFormatMode.HOUR_24.value,
            showOvertimeShade = prefs[Keys.OVERTIME_SHADE] ?: true,
            defaultInputMode = prefs[Keys.DEFAULT_INPUT] ?: "OCR",
            ocrAutoFill = prefs[Keys.OCR_AUTO_FILL] ?: true,
            gallerySaveEnabled = prefs[Keys.GALLERY_SAVE] ?: true,
            statusMapping = prefs[Keys.STATUS_MAPPING] ?: "",
            themeMode = prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.value,
            habits = prefs[Keys.HABITS] ?: "",
            customThemeEnabled = prefs[Keys.CUSTOM_THEME] ?: false,
            customLightColors = prefs[Keys.CUSTOM_LIGHT] ?: "",
            customDarkColors = prefs[Keys.CUSTOM_DARK] ?: "",
            fontScale = prefs[Keys.FONT_SCALE] ?: 1f,
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
            reminderType = prefs[Keys.REMINDER_TYPE] ?: ReminderType.OVERTIME.value,
            reminderMinutes = prefs[Keys.REMINDER_MINUTES] ?: 420,
            lastReminderKey = prefs[Keys.LAST_REMINDER_KEY] ?: ""
        )
    }

    suspend fun setCalendarDisplayMode(mode: String) =
        context.dataStore.edit { it[Keys.CALENDAR_DISPLAY] = mode }

    suspend fun setTimeFormat(mode: String) =
        context.dataStore.edit { it[Keys.TIME_FORMAT] = mode }

    suspend fun setOvertimeShade(enabled: Boolean) =
        context.dataStore.edit { it[Keys.OVERTIME_SHADE] = enabled }

    suspend fun setOcrAutoFill(enabled: Boolean) =
        context.dataStore.edit { it[Keys.OCR_AUTO_FILL] = enabled }

    suspend fun setDefaultInputMode(mode: String) =
        context.dataStore.edit { it[Keys.DEFAULT_INPUT] = mode }

    suspend fun setGallerySaveEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.GALLERY_SAVE] = enabled }

    suspend fun setStatusMapping(value: String) =
        context.dataStore.edit { it[Keys.STATUS_MAPPING] = value }

    suspend fun setThemeMode(mode: String) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }

    suspend fun setHabits(value: String) =
        context.dataStore.edit { it[Keys.HABITS] = value }

    suspend fun setCustomThemeEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.CUSTOM_THEME] = enabled }

    suspend fun setCustomLightColors(value: String) =
        context.dataStore.edit { it[Keys.CUSTOM_LIGHT] = value }

    suspend fun setCustomDarkColors(value: String) =
        context.dataStore.edit { it[Keys.CUSTOM_DARK] = value }

    suspend fun setFontScale(value: Float) =
        context.dataStore.edit { it[Keys.FONT_SCALE] = value.coerceIn(0.8f, 1.3f) }

    suspend fun setReminderEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }

    suspend fun setReminderType(value: String) =
        context.dataStore.edit { it[Keys.REMINDER_TYPE] = value }

    suspend fun setReminderMinutes(value: Int) =
        context.dataStore.edit { it[Keys.REMINDER_MINUTES] = value.coerceIn(60, 720) }

    suspend fun setLastReminderKey(value: String) =
        context.dataStore.edit { it[Keys.LAST_REMINDER_KEY] = value }
}
