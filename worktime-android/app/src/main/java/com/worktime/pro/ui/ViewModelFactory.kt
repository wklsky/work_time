/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/ViewModelFactory.kt
 * @Description: ViewModel 工厂，手动注入依赖并缓存 CameraViewModel 实例以在拍照页与确认页间共享 bitmap
 */
package com.worktime.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.backup.BackupManager
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.ui.camera.CameraViewModel
import com.worktime.pro.ui.detail.DayDetailViewModel
import com.worktime.pro.ui.home.HomeViewModel
import com.worktime.pro.ui.manual.ManualViewModel
import com.worktime.pro.ui.settings.BackupViewModel
import com.worktime.pro.ui.settings.SettingsViewModel
import com.worktime.pro.ui.summary.SummaryViewModel

class ViewModelFactory(
    private val repository: WorkTimeRepository,
    private val settings: SettingsDataStore,
    private val backupManager: BackupManager
) : ViewModelProvider.Factory {
    /** 拍照页捕获的 bitmap，交由确认页识别，跨页面共享同一 ViewModel 实例 */
    val sharedCameraViewModel: CameraViewModel by lazy { CameraViewModel(repository, settings) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(repository, settings) as T
        modelClass.isAssignableFrom(DayDetailViewModel::class.java) ->
            DayDetailViewModel("", repository, settings) as T
        modelClass.isAssignableFrom(CameraViewModel::class.java) ->
            sharedCameraViewModel as T
        modelClass.isAssignableFrom(ManualViewModel::class.java) ->
            ManualViewModel(repository, settings) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(settings, repository) as T
        modelClass.isAssignableFrom(SummaryViewModel::class.java) ->
            SummaryViewModel(repository, settings) as T
        modelClass.isAssignableFrom(BackupViewModel::class.java) ->
            BackupViewModel(backupManager, settings) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
