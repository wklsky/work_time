package com.worktime.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.backup.BackupManager
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.ui.detail.DayDetailViewModel
import com.worktime.pro.ui.habit.HabitViewModel
import com.worktime.pro.ui.home.HomeViewModel
import com.worktime.pro.ui.manual.ManualViewModel
import com.worktime.pro.ui.mine.MineViewModel
import com.worktime.pro.ui.rules.WorkRulesViewModel
import com.worktime.pro.ui.settings.BackupViewModel
import com.worktime.pro.ui.settings.SettingsViewModel
import com.worktime.pro.ui.summary.SummaryViewModel
import com.worktime.pro.ui.theme.CustomThemeViewModel
import com.worktime.pro.ui.today.TodayViewModel

class ViewModelFactory(
    private val repository: WorkTimeRepository,
    private val settings: SettingsDataStore,
    private val backupManager: BackupManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(repository, settings) as T
        modelClass.isAssignableFrom(DayDetailViewModel::class.java) ->
            DayDetailViewModel("", repository, settings) as T
        modelClass.isAssignableFrom(ManualViewModel::class.java) ->
            ManualViewModel(repository, settings) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(settings, repository) as T
        modelClass.isAssignableFrom(SummaryViewModel::class.java) ->
            SummaryViewModel(repository, settings) as T
        modelClass.isAssignableFrom(BackupViewModel::class.java) ->
            BackupViewModel(backupManager, settings) as T
        modelClass.isAssignableFrom(TodayViewModel::class.java) ->
            TodayViewModel(repository, settings) as T
        modelClass.isAssignableFrom(HabitViewModel::class.java) ->
            HabitViewModel(settings) as T
        modelClass.isAssignableFrom(WorkRulesViewModel::class.java) ->
            WorkRulesViewModel(repository) as T
        modelClass.isAssignableFrom(CustomThemeViewModel::class.java) ->
            CustomThemeViewModel(settings) as T
        modelClass.isAssignableFrom(MineViewModel::class.java) ->
            MineViewModel(repository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
