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
import com.worktime.pro.util.ColorContrast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PaletteSlot(val label: String, val description: String) {
    PRIMARY("品牌色", "标题、主按钮与选中态"),
    WORK("正常工时", "正常工时与上班标签"),
    OVERTIME("加班", "加班标签与今日圆环"),
    BACKGROUND("页面背景", "页面与日历底色"),
    SURFACE("卡片表面", "卡片与弹层底色"),
    TEXT_PRIMARY("正文文字", "标题与正文"),
    TEXT_SECONDARY("次要文字", "说明与图例")
}

fun CustomPalette.colorOf(slot: PaletteSlot): Color = when (slot) {
    PaletteSlot.PRIMARY -> primary
    PaletteSlot.WORK -> work
    PaletteSlot.OVERTIME -> overtime
    PaletteSlot.BACKGROUND -> background
    PaletteSlot.SURFACE -> surface
    PaletteSlot.TEXT_PRIMARY -> textPrimary
    PaletteSlot.TEXT_SECONDARY -> textSecondary
}

fun CustomPalette.withColor(slot: PaletteSlot, color: Color): CustomPalette = when (slot) {
    PaletteSlot.PRIMARY -> copy(primary = color)
    PaletteSlot.WORK -> copy(work = color)
    PaletteSlot.OVERTIME -> copy(overtime = color)
    PaletteSlot.BACKGROUND -> copy(background = color)
    PaletteSlot.SURFACE -> copy(surface = color)
    PaletteSlot.TEXT_PRIMARY -> copy(textPrimary = color)
    PaletteSlot.TEXT_SECONDARY -> copy(textSecondary = color)
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
        val updated = current.palette.withColor(slot, color)
        val primaryContrast = ColorContrast.ratio(updated.textPrimary, updated.background)
        val secondaryContrast = ColorContrast.ratio(updated.textSecondary, updated.background)
        val surfaceContrast = ColorContrast.ratio(updated.textPrimary, updated.surface)
        if (primaryContrast < 3f || secondaryContrast < 3f || surfaceContrast < 3f) {
            val reason = when {
                primaryContrast < 3f -> "正文与页面背景"
                secondaryContrast < 3f -> "次要文字与页面背景"
                else -> "正文与卡片表面"
            }
            _state.value = current.copy(
                message = "$reason对比度过低，文字会难以辨认，已取消本次修改"
            )
            return
        }
        val warning = when {
            primaryContrast < 4.5f -> "提示：正文与页面背景对比度低于 4.5:1"
            secondaryContrast < 4.5f -> "提示：次要文字与页面背景对比度低于 4.5:1"
            surfaceContrast < 4.5f -> "提示：正文与卡片表面对比度低于 4.5:1"
            else -> null
        }
        _state.value = current.copy(
            light = if (current.editDark) current.light else updated,
            dark = if (current.editDark) updated else current.dark,
            message = warning
        )
        persist(current.editDark, updated)
    }

    /** 只恢复当前颜色的默认值，保留同模式其他自定义色 */
    fun resetSlot(slot: PaletteSlot) {
        val current = _state.value
        val defaults = if (current.editDark) {
            CustomPalette.defaultDark()
        } else {
            CustomPalette.defaultLight()
        }
        val updated = current.palette.withColor(slot, defaults.colorOf(slot))
        _state.value = current.copy(
            light = if (current.editDark) current.light else updated,
            dark = if (current.editDark) updated else current.dark,
            message = "已恢复「${slot.label}」默认色"
        )
        persist(current.editDark, updated)
    }

    fun setFontScale(scale: Float) {
        _state.value = _state.value.copy(fontScale = scale)
        viewModelScope.launch { settings.setFontScale(scale) }
    }

    fun resetPalette() {
        val current = _state.value
        val updated = if (current.editDark) {
            CustomPalette.defaultDark()
        } else {
            CustomPalette.defaultLight()
        }
        _state.value = current.copy(
            light = if (current.editDark) current.light else updated,
            dark = if (current.editDark) updated else current.dark,
            message = "已恢复默认配色"
        )
        persist(current.editDark, updated)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun colorOf(slot: PaletteSlot): Color = _state.value.palette.colorOf(slot)

    fun defaultColorOf(slot: PaletteSlot): Color {
        val defaults = if (_state.value.editDark) {
            CustomPalette.defaultDark()
        } else {
            CustomPalette.defaultLight()
        }
        return defaults.colorOf(slot)
    }

    private fun persist(dark: Boolean, palette: CustomPalette) {
        viewModelScope.launch {
            if (dark) {
                settings.setCustomDarkColors(palette.serialize())
            } else {
                settings.setCustomLightColors(palette.serialize())
            }
        }
    }
}
