/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/theme/Theme.kt
 * @Description: Material3 主题定义，支持跟随系统/浅色/深色/黑白四种模式
 */
package com.worktime.pro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.worktime.pro.data.datastore.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFF26A69A),
    background = Color(0xFFFAFAFA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8DA),
    secondary = Color(0xFF7986CB),
    tertiary = Color(0xFF4DB6AC)
)

/**
 * 黑白主题：纯灰阶高对比，所有强调色均为灰白，背景纯黑、表面深灰。
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
    error = Color(0xFFFFFFFF),
    onError = Color(0xFF000000),
    outline = Color(0xFF616161)
)

@Composable
fun WorkTimeTheme(
    themeMode: String = ThemeMode.SYSTEM.value,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val mode = ThemeMode.entries.firstOrNull { it.value == themeMode } ?: ThemeMode.SYSTEM

    val colorScheme = when (mode) {
        ThemeMode.BLACK_WHITE -> BlackWhiteColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.SYSTEM -> {
            // 跟随系统时优先用 Android 12+ 动态配色，保证与系统外观一致
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColors else LightColors
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
