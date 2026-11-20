/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/habit/HabitViewModel.kt
 * @Description: 习惯打卡状态管理，保存与读取用户自定义的固定时间打卡模板
 */
package com.worktime.pro.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.PunchHabit
import com.worktime.pro.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitViewModel(
    private val settings: SettingsDataStore
) : ViewModel() {

    val habits: StateFlow<List<PunchHabit>> = settings.settings
        .map { PunchHabit.deserialize(it.habits) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 新增或更新习惯：按 id 匹配，存在则替换，否则追加 */
    fun saveHabit(habit: PunchHabit, onDone: () -> Unit) {
        viewModelScope.launch {
            val current = PunchHabit.deserialize(settings.settings.first().habits).toMutableList()
            val index = current.indexOfFirst { it.id == habit.id }
            if (index >= 0) current[index] = habit else current.add(habit)
            settings.setHabits(PunchHabit.serialize(current))
            onDone()
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch {
            val current = PunchHabit.deserialize(settings.settings.first().habits)
                .filterNot { it.id == id }
            settings.setHabits(PunchHabit.serialize(current))
        }
    }
}
