/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/util/ColorContrast.kt
 * @Description: WCAG 对比度与前景色选择，供主题、取色器和可读性检查共用
 */
package com.worktime.pro.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

enum class ContrastLevel { GOOD, WARN, POOR }

object ColorContrast {

    /** 根据背景亮度返回黑/白前景，保证按钮文字可读 */
    fun on(color: Color): Color {
        val l = color.luminance()
        val white = 1.05f / (l + 0.05f)
        val black = (l + 0.05f) / 0.05f
        return if (white >= black) Color.White else Color.Black
    }

    /** WCAG 对比度；正文建议 ≥ 4.5，低于 3 视为不可读 */
    fun ratio(foreground: Color, background: Color): Float {
        val l1 = max(foreground.luminance(), background.luminance()) + 0.05f
        val l2 = min(foreground.luminance(), background.luminance()) + 0.05f
        return l1 / l2
    }

    fun level(ratio: Float): ContrastLevel = when {
        ratio >= 4.5f -> ContrastLevel.GOOD
        ratio >= 3f -> ContrastLevel.WARN
        else -> ContrastLevel.POOR
    }
}
