/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/rules/WorkRulesViewModel.kt
 * @Description: 工作规则状态管理，读写默认规则并支持按新规则重算全部历史
 */
package com.worktime.pro.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.db.WorkRuleEntity
import com.worktime.pro.domain.repository.WorkTimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class WorkRuleUiState(
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endTime: LocalTime = LocalTime.of(18, 0),
    val restEnabled: Boolean = true,
    val restStart: LocalTime = LocalTime.of(12, 0),
    val restEnd: LocalTime = LocalTime.of(13, 0),
    val bufferMinutes: Int = 30,
    val dayCutTime: LocalTime = LocalTime.of(4, 0),
    val workDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null
) {
    val spanMinutes: Int
        get() = Duration.between(startTime, endTime).toMinutes().toInt().coerceAtLeast(0)

    val restMinutes: Int
        get() = if (restEnabled) {
            Duration.between(restStart, restEnd).toMinutes().toInt().coerceAtLeast(0)
        } else 0

    /** 与引擎口径一致：标准工时 = 在岗时长 - 午休 */
    val standardMinutes: Int get() = (spanMinutes - restMinutes).coerceAtLeast(0)
}

class WorkRulesViewModel(
    private val repository: WorkTimeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkRuleUiState())
    val state: StateFlow<WorkRuleUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val current = _state.value
            val loaded = runCatching {
                val rule = repository.getWorkRule() ?: return@runCatching null
                current.copy(
                    startTime = LocalTime.parse(rule.startTime),
                    endTime = LocalTime.parse(rule.endTime),
                    restEnabled = rule.restStart != null && rule.restEnd != null,
                    restStart = rule.restStart?.let(LocalTime::parse) ?: LocalTime.of(12, 0),
                    restEnd = rule.restEnd?.let(LocalTime::parse) ?: LocalTime.of(13, 0),
                    bufferMinutes = rule.bufferMinutes,
                    dayCutTime = LocalTime.parse(rule.dayCutTime),
                    workDays = rule.workDays.toSet(),
                    isLoading = false
                )
            }.getOrNull()
            _state.value = loaded ?: current.copy(isLoading = false)
        }
    }

    fun setStartTime(value: LocalTime) {
        _state.value = _state.value.copy(startTime = value)
    }

    fun setEndTime(value: LocalTime) {
        _state.value = _state.value.copy(endTime = value)
    }

    fun setRestEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(restEnabled = enabled)
    }

    fun setRestStart(value: LocalTime) {
        _state.value = _state.value.copy(restStart = value)
    }

    fun setRestEnd(value: LocalTime) {
        _state.value = _state.value.copy(restEnd = value)
    }

    fun setBufferMinutes(value: Int) {
        _state.value = _state.value.copy(bufferMinutes = value.coerceIn(0, 180))
    }

    fun setDayCutTime(value: LocalTime) {
        _state.value = _state.value.copy(dayCutTime = value)
    }

    fun toggleWorkDay(day: Int) {
        val current = _state.value.workDays
        val updated = if (day in current) current - day else current + day
        _state.value = _state.value.copy(workDays = updated)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /**
     * 保存规则。recalculate=true 时用新规则重算全部历史记录；
     * 默认只影响之后新写入或重新计算的日期，避免静默改动用户历史。
     */
    fun save(recalculate: Boolean, onDone: (String) -> Unit) {
        val current = _state.value
        val error = validate(current)
        if (error != null) {
            _state.value = current.copy(message = error)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, message = null)
            runCatching {
                repository.saveWorkRule(current.toEntity())
                if (recalculate) repository.recalculateAll() else 0
            }.onSuccess { count ->
                val message = if (recalculate) {
                    "已保存，并重算 $count 天记录"
                } else {
                    "已保存，新记录将按新规则计算"
                }
                _state.value = _state.value.copy(isSaving = false, message = message)
                onDone(message)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isSaving = false,
                    message = "保存失败：${error.message ?: "未知错误"}"
                )
            }
        }
    }

    private fun validate(state: WorkRuleUiState): String? = when {
        state.workDays.isEmpty() -> "请至少选择一个工作日"
        !state.endTime.isAfter(state.startTime) -> "下班时间需晚于上班时间"
        state.restEnabled && !state.restEnd.isAfter(state.restStart) -> "午休结束时间需晚于开始时间"
        state.bufferMinutes !in 0..180 -> "加班缓冲需在 0–180 分钟之间"
        else -> null
    }

    private fun WorkRuleUiState.toEntity() = WorkRuleEntity(
        ruleName = "默认规则",
        startTime = startTime.format(TIME_FORMAT),
        endTime = endTime.format(TIME_FORMAT),
        restStart = if (restEnabled) restStart.format(TIME_FORMAT) else null,
        restEnd = if (restEnabled) restEnd.format(TIME_FORMAT) else null,
        bufferMinutes = bufferMinutes,
        dayCutTime = dayCutTime.format(TIME_FORMAT),
        workDays = workDays.sorted(),
        isDefault = true,
        updateTime = 0
    )

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
