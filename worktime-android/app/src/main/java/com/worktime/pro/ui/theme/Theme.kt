/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/theme/Theme.kt
 * @Description: Material3 主题定义，「工时台账」6 色系统 + 等宽数字排版 + 自定义配色/字号
 */
package com.worktime.pro.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.worktime.pro.data.datastore.ThemeMode

// 「工时台账」6 色语义系统：ink 墨蓝为品牌主色，work 工时蓝表示正常工时，overtime 打卡橙表示加班
private val Ink = Color(0xFF1B2A41)
private val WorkBlue = Color(0xFF3A6EA5)
private val OvertimeOrange = Color(0xFFE07A3F)
private val Paper = Color(0xFFF5F7FA)
private val Surface = Color(0xFFFFFFFF)
private val Muted = Color(0xFF8492A6)

/**
 * 浅色配色：把 6 色映射到 Material3 槽位。
 * 约定 secondary=工时蓝、tertiary=加班橙，页面组件据此区分正常工时与加班，避免各处硬编码色值。
 */
private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = WorkBlue,
    onSecondary = Color.White,
    tertiary = OvertimeOrange,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF5),
    surfaceContainerHighest = Color(0xFFE1E7F0),
    primaryContainer = Color(0xFFE4EAF3),
    onPrimaryContainer = Ink,
    secondaryContainer = Color(0xFFE1EBF6),
    onSecondaryContainer = Color(0xFF264E78),
    tertiaryContainer = Color(0xFFFBE9DF),
    onTertiaryContainer = Color(0xFF8A3D14),
    outline = Color(0xFFD5DBE3),
    outlineVariant = Color(0xFFE4E8EE)
)

/** 深色配色：保持墨蓝/工时蓝/加班橙的品牌语义，仅调整为暗底可读的亮度 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB4E8),
    onPrimary = Color(0xFF0E1A2B),
    secondary = Color(0xFF7FA9D8),
    onSecondary = Color(0xFF0E1A2B),
    tertiary = OvertimeOrange,
    onTertiary = Color(0xFF1A1005),
    background = Color(0xFF0F1620),
    onBackground = Color(0xFFE8EDF4),
    surface = Color(0xFF182130),
    onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF222D3E),
    onSurfaceVariant = Color(0xFFA3AEBE),
    surfaceContainerLowest = Color(0xFF101925),
    surfaceContainerLow = Color(0xFF141E2B),
    surfaceContainer = Color(0xFF182130),
    surfaceContainerHigh = Color(0xFF1E293A),
    surfaceContainerHighest = Color(0xFF243145),
    primaryContainer = Color(0xFF243A57),
    onPrimaryContainer = Color(0xFFC5D9F5),
    secondaryContainer = Color(0xFF1F3B57),
    onSecondaryContainer = Color(0xFFB8D2F0),
    tertiaryContainer = Color(0xFF4A2A16),
    onTertiaryContainer = Color(0xFFFFC9A8),
    outline = Color(0xFF3A4658),
    outlineVariant = Color(0xFF2B3648)
)

/**
 * 黑白主题：纯灰阶高对比，补齐全部容器槽位，避免回落到 Material3 默认紫灰。
 * 业务定位：给需要极简、省电（OLED 纯黑不发光）或高对比可读性的用户使用。
 */
private val BlackWhiteColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF242424),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF1E1E1E),
    onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFFFFFFF),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF2A2A2A),
    onErrorContainer = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
    inversePrimary = Color(0xFF000000),
    outline = Color(0xFF616161),
    outlineVariant = Color(0xFF3A3A3A)
)

private const val TABULAR_NUMBERS = "tnum"

/** 全层级等宽数字 + 可选字号缩放，保证时长/日期纵向对齐 */
private fun Typography.withFontScale(factor: Float): Typography {
    fun TextStyle.scaled(): TextStyle = copy(
        fontSize = if (fontSize == TextUnit.Unspecified) fontSize else fontSize * factor,
        lineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else lineHeight * factor,
        fontFeatureSettings = TABULAR_NUMBERS
    )
    return Typography(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled()
    )
}

/** 统一的圆角语言：标签小、卡片中、弹层大 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WorkTimeTheme(
    themeMode: String = ThemeMode.SYSTEM.value,
    customThemeEnabled: Boolean = false,
    customLight: String = "",
    customDark: String = "",
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val mode = ThemeMode.entries.firstOrNull { it.value == themeMode } ?: ThemeMode.SYSTEM
    val effectiveDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.BLACK_WHITE -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme: ColorScheme = when {
        mode == ThemeMode.BLACK_WHITE -> BlackWhiteColors
        customThemeEnabled -> {
            val palette = if (effectiveDark) {
                CustomPalette.deserialize(customDark) ?: CustomPalette.defaultDark()
            } else {
                CustomPalette.deserialize(customLight) ?: CustomPalette.defaultLight()
            }
            palette.toColorScheme()
        }
        mode == ThemeMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> if (effectiveDark) DarkColors else LightColors
    }

    ConfigureSystemBars(colorScheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography().withFontScale(fontScale.coerceIn(0.8f, 1.3f)),
        shapes = AppShapes,
        content = content
    )
}

/** 状态栏/导航栏图标随背景亮度切换，配合 MainActivity 的 edge-to-edge */
@Composable
private fun ConfigureSystemBars(colorScheme: ColorScheme) {
    val view = LocalView.current
    val dark = colorScheme.background.luminance() < 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
