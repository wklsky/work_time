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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val timeFormat: String = TimeFormatMode.SYSTEM.value,
    val showOvertimeShade: Boolean = true,
    val defaultInputMode: String = "OCR",
    val ocrAutoFill: Boolean = true,
    val backupIncludePhotos: Boolean = false,
    /** 用户自定义的考勤机状态映射，序列化字符串；空串表示使用默认映射 */
    val statusMapping: String = "",
    /** 主题模式，默认跟随系统 */
    val themeMode: String = ThemeMode.SYSTEM.value
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val CALENDAR_DISPLAY = stringPreferencesKey("calendar_display_mode")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val OVERTIME_SHADE = booleanPreferencesKey("show_overtime_shade")
        val DEFAULT_INPUT = stringPreferencesKey("default_input_mode")
        val OCR_AUTO_FILL = booleanPreferencesKey("ocr_auto_fill")
        val BACKUP_PHOTOS = booleanPreferencesKey("backup_include_photos")
        val STATUS_MAPPING = stringPreferencesKey("status_mapping")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            calendarDisplayMode = prefs[Keys.CALENDAR_DISPLAY] ?: CalendarDisplayMode.WORK_TIME.value,
            timeFormat = prefs[Keys.TIME_FORMAT] ?: TimeFormatMode.SYSTEM.value,
            showOvertimeShade = prefs[Keys.OVERTIME_SHADE] ?: true,
            defaultInputMode = prefs[Keys.DEFAULT_INPUT] ?: "OCR",
            ocrAutoFill = prefs[Keys.OCR_AUTO_FILL] ?: true,
            backupIncludePhotos = prefs[Keys.BACKUP_PHOTOS] ?: false,
            statusMapping = prefs[Keys.STATUS_MAPPING] ?: "",
            themeMode = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.value
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

    suspend fun setBackupIncludePhotos(enabled: Boolean) =
        context.dataStore.edit { it[Keys.BACKUP_PHOTOS] = enabled }

    suspend fun setStatusMapping(value: String) =
        context.dataStore.edit { it[Keys.STATUS_MAPPING] = value }

    suspend fun setThemeMode(mode: String) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
}
