/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/theme/CustomThemeViewModel.kt
 * @Description: 自定义配色状态管理，实时写入 DataStore 并做对比度校验
 */
package com.worktime.pro.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PaletteSlot(val label: String) {
    PRIMARY("品牌色"),
    WORK("正常工时"),
    OVERTIME("加班"),
    BACKGROUND("页面背景"),
    SURFACE("卡片表面"),
    TEXT_PRIMARY("正文文字"),
    TEXT_SECONDARY("次要文字")
}

data class CustomThemeUiState(
    val enabled: Boolean = false,
    val editDark: Boolean = false,
    val light: CustomPalette = CustomPalette.defaultLight(),
    val dark: CustomPalette = CustomPalette.defaultDark(),
    val fontScale: Float = 1f,
    val message: String? = null
) {
    val palette: CustomPalette get() = if (editDark) dark else light
}

class CustomThemeViewModel(
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(CustomThemeUiState())
    val state: StateFlow<CustomThemeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settings.settings.first()
            _state.value = CustomThemeUiState(
                enabled = saved.customThemeEnabled,
                light = CustomPalette.deserialize(saved.customLightColors)
                    ?: CustomPalette.defaultLight(),
                dark = CustomPalette.deserialize(saved.customDarkColors)
                    ?: CustomPalette.defaultDark(),
                fontScale = saved.fontScale
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
        viewModelScope.launch { settings.setCustomThemeEnabled(enabled) }
    }

    fun setEditDark(dark: Boolean) {
        _state.value = _state.value.copy(editDark = dark)
    }

    fun applyPreset(preset: PalettePreset) {
        _state.value = _state.value.copy(
            light = preset.light,
            dark = preset.dark,
            message = "已应用「${preset.name}」"
        )
        viewModelScope.launch {
            settings.setCustomLightColors(preset.light.serialize())
            settings.setCustomDarkColors(preset.dark.serialize())
        }
    }

    fun updateColor(slot: PaletteSlot, color: Color) {
        val current = _state.value
        val palette = current.palette
        val updated = when (slot) {
            PaletteSlot.PRIMARY -> palette.copy(primary = color)
            PaletteSlot.WORK -> palette.copy(work = color)
            PaletteSlot.OVERTIME -> palette.copy(overtime = color)
            PaletteSlot.BACKGROUND -> palette.copy(background = color)
            PaletteSlot.SURFACE -> palette.copy(surface = color)
            PaletteSlot.TEXT_PRIMARY -> palette.copy(textPrimary = color)
            PaletteSlot.TEXT_SECONDARY -> palette.copy(textSecondary = color)
        }
        val primaryContrast = CustomPalette.contrastRatio(updated.textPrimary, updated.background)
        val secondaryContrast = CustomPalette.contrastRatio(updated.textSecondary, updated.background)
        val surfaceContrast = CustomPalette.contrastRatio(updated.textPrimary, updated.surface)
        if (primaryContrast < 3f || secondaryContrast < 3f || surfaceContrast < 3f) {
            _state.value = current.copy(message = "对比度过低，文字会难以辨认，已取消本次修改")
            return
        }
        val warning = if (
            primaryContrast < 4.5f || secondaryContrast < 4.5f || surfaceContrast < 4.5f
        ) {
            "提示：当前文字对比度低于 4.5:1，长时间阅读可能吃力"
        } else null
        _state.value = current.copy(
            light = if (current.editDark) current.light else updated,
            dark = if (current.editDark) updated else current.dark,
            message = warning
        )
        viewModelScope.launch {
            if (current.editDark) {
                settings.setCustomDarkColors(updated.serialize())
            } else {
                settings.setCustomLightColors(updated.serialize())
            }
        }
    }

    fun setFontScale(scale: Float) {
        _state.value = _state.value.copy(fontScale = scale)
        viewModelScope.launch { settings.setFontScale(scale) }
    }

    fun resetPalette() {
        val current = _state.value
        val updated = if (current.editDark) CustomPalette.defaultDark() else CustomPalette.defaultLight()
        _state.value = current.copy(
            light = if (current.editDark) current.light else updated,
            dark = if (current.editDark) updated else current.dark,
            message = "已恢复默认配色"
        )
        viewModelScope.launch {
            if (current.editDark) {
                settings.setCustomDarkColors(updated.serialize())
            } else {
                settings.setCustomLightColors(updated.serialize())
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun colorOf(slot: PaletteSlot): Color = when (slot) {
        PaletteSlot.PRIMARY -> _state.value.palette.primary
        PaletteSlot.WORK -> _state.value.palette.work
        PaletteSlot.OVERTIME -> _state.value.palette.overtime
        PaletteSlot.BACKGROUND -> _state.value.palette.background
        PaletteSlot.SURFACE -> _state.value.palette.surface
        PaletteSlot.TEXT_PRIMARY -> _state.value.palette.textPrimary
        PaletteSlot.TEXT_SECONDARY -> _state.value.palette.textSecondary
    }
}
