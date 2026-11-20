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
    /**
     * 常用预设，按色相环从蓝到中性色排列，便于横向扫视。
     * 每套同时提供浅色与深色；新增的六套按浅色次要文字/背景 ≥ 4.5:1 校验过，
     * 深色模式则统一提亮品牌色与工时/加班色，保证暗底可读。
     */
    val ALL = listOf(
        PalettePreset("工时台账", CustomPalette.defaultLight(), CustomPalette.defaultDark()),
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
        ),
        PalettePreset(
            name = "靛青",
            light = CustomPalette(
                primary = Color(0xFF2E2A5E),
                work = Color(0xFF5B57A6),
                overtime = Color(0xFFB55027),
                background = Color(0xFFF5F3FA),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF211E33),
                textSecondary = Color(0xFF6E6A85)
            ),
            dark = CustomPalette(
                primary = Color(0xFFBCB4F0),
                work = Color(0xFFA6A0E0),
                overtime = Color(0xFFE8A06E),
                background = Color(0xFF100F18),
                surface = Color(0xFF191725),
                textPrimary = Color(0xFFE9E6F5),
                textSecondary = Color(0xFFA8A2BE)
            )
        ),
        PalettePreset(
            name = "绛紫",
            light = CustomPalette(
                primary = Color(0xFF4A2640),
                work = Color(0xFF7A3F66),
                overtime = Color(0xFFB04D30),
                background = Color(0xFFF7F2F6),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF2B1A27),
                textSecondary = Color(0xFF7A6574)
            ),
            dark = CustomPalette(
                primary = Color(0xFFD9AECE),
                work = Color(0xFFC08AAE),
                overtime = Color(0xFFE89E86),
                background = Color(0xFF160F15),
                surface = Color(0xFF221721),
                textPrimary = Color(0xFFF3E6F0),
                textSecondary = Color(0xFFB9A3B0)
            )
        ),
        PalettePreset(
            name = "玫瑰",
            light = CustomPalette(
                primary = Color(0xFF6B2F3A),
                work = Color(0xFFA34F63),
                overtime = Color(0xFFA84E22),
                background = Color(0xFFFBF4F3),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF341E21),
                textSecondary = Color(0xFF856A6B)
            ),
            dark = CustomPalette(
                primary = Color(0xFFE6A9B4),
                work = Color(0xFFD98A9B),
                overtime = Color(0xFFE79C7E),
                background = Color(0xFF170F11),
                surface = Color(0xFF231619),
                textPrimary = Color(0xFFF7E9EB),
                textSecondary = Color(0xFFC2A3A8)
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
            name = "琥珀",
            light = CustomPalette(
                primary = Color(0xFF4A3512),
                work = Color(0xFF2E6B5B),
                overtime = Color(0xFF9A6212),
                background = Color(0xFFFAF6EC),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF2E2415),
                textSecondary = Color(0xFF786A50)
            ),
            dark = CustomPalette(
                primary = Color(0xFFE8C77E),
                work = Color(0xFF9CCB9A),
                overtime = Color(0xFFE0A64C),
                background = Color(0xFF161208),
                surface = Color(0xFF221C10),
                textPrimary = Color(0xFFF5EEDC),
                textSecondary = Color(0xFFC0B08F)
            )
        ),
        PalettePreset(
            name = "青瓷",
            light = CustomPalette(
                primary = Color(0xFF1F4A44),
                work = Color(0xFF2E7A6B),
                overtime = Color(0xFFA9572E),
                background = Color(0xFFF1F6F3),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF1A2A26),
                textSecondary = Color(0xFF5F726A)
            ),
            dark = CustomPalette(
                primary = Color(0xFF9CCFC2),
                work = Color(0xFF7CC3AE),
                overtime = Color(0xFFE0A276),
                background = Color(0xFF0D1614),
                surface = Color(0xFF152320),
                textPrimary = Color(0xFFE4EFEA),
                textSecondary = Color(0xFF9DB3AB)
            )
        ),
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
            name = "石墨",
            light = CustomPalette(
                primary = Color(0xFF343A40),
                work = Color(0xFF4F6D8A),
                overtime = Color(0xFFB4532F),
                background = Color(0xFFF4F5F6),
                surface = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF24272B),
                textSecondary = Color(0xFF676D75)
            ),
            dark = CustomPalette(
                primary = Color(0xFFC9CED6),
                work = Color(0xFF8FB0CE),
                overtime = Color(0xFFE39A6E),
                background = Color(0xFF121417),
                surface = Color(0xFF1B2025),
                textPrimary = Color(0xFFE7EAEE),
                textSecondary = Color(0xFFA4ABB4)
            )
        )
    )
}
