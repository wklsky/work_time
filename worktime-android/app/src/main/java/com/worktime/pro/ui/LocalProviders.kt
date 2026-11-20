/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/LocalProviders.kt
 * @Description: CompositionLocal 定义，在 Compose 树中共享 Repository 与 Settings 实例
 */
package com.worktime.pro.ui

import androidx.compose.runtime.compositionLocalOf
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.repository.WorkTimeRepository

val LocalRepository = compositionLocalOf<WorkTimeRepository> {
    error("WorkTimeRepository 未提供")
}

val LocalSettings = compositionLocalOf<SettingsDataStore> {
    error("SettingsDataStore 未提供")
}
