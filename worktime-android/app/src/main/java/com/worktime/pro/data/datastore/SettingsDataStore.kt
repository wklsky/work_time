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

/** 日历下方显示模式：全局统一，在设置中选择一次，影响所有日期格子 */
enum class CalendarDisplayMode(val value: String) {
    WORK_TIME("WORK_TIME"),
    OVERTIME("OVERTIME")
}

enum class TimeFormatMode(val value: String) {
    SYSTEM("SYSTEM"),
    HOUR_24("24H"),
    HOUR_12("12H")
}

data class AppSettings(
    val calendarDisplayMode: String = CalendarDisplayMode.WORK_TIME.value,
    val timeFormat: String = TimeFormatMode.SYSTEM.value,
    val showOvertimeShade: Boolean = true,
    val defaultInputMode: String = "OCR",
    val ocrAutoFill: Boolean = true,
    val backupIncludePhotos: Boolean = false
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val CALENDAR_DISPLAY = stringPreferencesKey("calendar_display_mode")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val OVERTIME_SHADE = booleanPreferencesKey("show_overtime_shade")
        val DEFAULT_INPUT = stringPreferencesKey("default_input_mode")
        val OCR_AUTO_FILL = booleanPreferencesKey("ocr_auto_fill")
        val BACKUP_PHOTOS = booleanPreferencesKey("backup_include_photos")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            calendarDisplayMode = prefs[Keys.CALENDAR_DISPLAY] ?: CalendarDisplayMode.WORK_TIME.value,
            timeFormat = prefs[Keys.TIME_FORMAT] ?: TimeFormatMode.SYSTEM.value,
            showOvertimeShade = prefs[Keys.OVERTIME_SHADE] ?: true,
            defaultInputMode = prefs[Keys.DEFAULT_INPUT] ?: "OCR",
            ocrAutoFill = prefs[Keys.OCR_AUTO_FILL] ?: true,
            backupIncludePhotos = prefs[Keys.BACKUP_PHOTOS] ?: false
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
}
