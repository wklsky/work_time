/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/theme/CustomPalette.kt
 * @Description: 用户自定义配色模型，负责序列化、槽位推导与 WCAG 对比度校验
 */
package com.worktime.pro.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.worktime.pro.util.ColorContrast

data class CustomPalette(
    val primary: Color,
    val work: Color,
    val overtime: Color,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color
) {
    fun serialize(): String = listOf(
        primary, work, overtime, background, surface, textPrimary, textSecondary
    ).joinToString(",") { it.toArgb().toString() }

    /** 由 7 个槽位推导完整 ColorScheme，容器与描边自动与背景/文字混合 */
    fun toColorScheme(): ColorScheme {
        val errorColor = Color(0xFFC0392B)
        val dark = background.luminance() < 0.5f
        val surfaceVariant = lerp(surface, textPrimary, 0.06f)
        val steps = if (dark) {
            listOf(0.25f, 0.40f, 0.55f, 0.70f, 0.85f)
        } else {
            listOf(0.00f, 0.25f, 0.40f, 0.55f, 0.70f)
        }
        fun container(step: Float) = lerp(surface, background, step)

        return lightColorScheme(
            primary = primary,
            onPrimary = contrastOn(primary),
            primaryContainer = lerp(surface, primary, 0.14f),
            onPrimaryContainer = textPrimary,
            secondary = work,
            onSecondary = contrastOn(work),
            secondaryContainer = lerp(surface, work, 0.14f),
            onSecondaryContainer = textPrimary,
            tertiary = overtime,
            onTertiary = contrastOn(overtime),
            tertiaryContainer = lerp(surface, overtime, 0.14f),
            onTertiaryContainer = textPrimary,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textSecondary,
            surfaceTint = primary,
            inverseSurface = textPrimary,
            inverseOnSurface = surface,
            inversePrimary = primary,
            error = errorColor,
            onError = Color.White,
            errorContainer = lerp(surface, errorColor, 0.14f),
            onErrorContainer = textPrimary,
            outline = lerp(background, textPrimary, 0.20f),
            outlineVariant = lerp(background, textPrimary, 0.12f),
            scrim = Color.Black,
            surfaceBright = if (dark) lerp(surface, Color.White, 0.10f) else surface,
            surfaceDim = container(0.60f),
            surfaceContainerLowest = container(steps[0]),
            surfaceContainerLow = container(steps[1]),
            surfaceContainer = container(steps[2]),
            surfaceContainerHigh = container(steps[3]),
            surfaceContainerHighest = container(steps[4])
        )
    }

    companion object {
        fun defaultLight() = CustomPalette(
            primary = Color(0xFF1B2A41),
            work = Color(0xFF3A6EA5),
            overtime = Color(0xFFE07A3F),
            background = Color(0xFFF5F7FA),
            surface = Color(0xFFFFFFFF),
            textPrimary = Color(0xFF1B2A41),
            textSecondary = Color(0xFF8492A6)
        )

        fun defaultDark() = CustomPalette(
            primary = Color(0xFF8FB4E8),
            work = Color(0xFF7FA9D8),
            overtime = Color(0xFFE08A5A),
            background = Color(0xFF0F1620),
            surface = Color(0xFF182130),
            textPrimary = Color(0xFFE8EDF4),
            textSecondary = Color(0xFFA3AEBE)
        )

        fun deserialize(raw: String): CustomPalette? {
            if (raw.isBlank()) return null
            val parts = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size != 7) return null
            return CustomPalette(
                primary = Color(parts[0]),
                work = Color(parts[1]),
                overtime = Color(parts[2]),
                background = Color(parts[3]),
                surface = Color(parts[4]),
                textPrimary = Color(parts[5]),
                textSecondary = Color(parts[6])
            )
        }

        /** 根据背景亮度自动选择黑/白前景，保证按钮文字可读 */
        fun contrastOn(color: Color): Color = ColorContrast.on(color)

        /** WCAG 对比度，正文/背景建议 ≥ 4.5，低于 3 时禁止保存 */
        fun contrastRatio(foreground: Color, background: Color): Float =
            ColorContrast.ratio(foreground, background)
    }
}

data class PalettePreset(
    val name: String,
    val light: CustomPalette,
    val dark: CustomPalette
)

object CustomPalettePresets {
    val ALL = listOf(
        PalettePreset("工时台账", CustomPalette.defaultLight(), CustomPalette.defaultDark()),
        PalettePreset(
            name = "墨绿",
            light = CustomPalette(
                primary = Color(0xFF1F3D2B),
                work = Color(0xFF3D7A57),
                overtime = Color(0xFFC97B3C),
                background = Color(0xFFF3F6F2),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF1F2A22),
                textSecondary = Color(0xFF7C8B80)
            ),
            dark = CustomPalette(
                primary = Color(0xFF8FC9A6),
                work = Color(0xFF6FBF94),
                overtime = Color(0xFFE0A36A),
                background = Color(0xFF0E1712),
                surface = Color(0xFF16231A),
                textPrimary = Color(0xFFE6F0E8),
                textSecondary = Color(0xFF9CB2A2)
            )
        ),
        PalettePreset(
            name = "赭石",
            light = CustomPalette(
                primary = Color(0xFF4A2E1F),
                work = Color(0xFF9C5B34),
                overtime = Color(0xFFC9821F),
                background = Color(0xFFF8F4EF),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF2E1F16),
                textSecondary = Color(0xFF8A7566)
            ),
            dark = CustomPalette(
                primary = Color(0xFFE0B08A),
                work = Color(0xFFD08A5C),
                overtime = Color(0xFFF2C94C),
                background = Color(0xFF17110D),
                surface = Color(0xFF241A14),
                textPrimary = Color(0xFFF2E8DF),
                textSecondary = Color(0xFFB8A492)
            )
        ),
        PalettePreset(
            name = "午夜蓝",
            light = CustomPalette(
                primary = Color(0xFF132A4A),
                work = Color(0xFF2F5F96),
                overtime = Color(0xFFE07A3F),
                background = Color(0xFFF2F5FA),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF122033),
                textSecondary = Color(0xFF7A8CA3)
            ),
            dark = CustomPalette(
                primary = Color(0xFF9CC0F0),
                work = Color(0xFF79A9E0),
                overtime = Color(0xFFE08A5A),
                background = Color(0xFF0A1220),
                surface = Color(0xFF121C2E),
                textPrimary = Color(0xFFE4ECF7),
                textSecondary = Color(0xFF9AAABF)
            )
        )
    )
}
