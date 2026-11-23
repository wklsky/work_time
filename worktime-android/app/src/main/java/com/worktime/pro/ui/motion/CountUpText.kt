/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/motion/CountUpText.kt
 * @Description: 数值滚动过渡：月份摘要、统计 KPI 等数字变化时从旧值滚到新值，替代瞬间跳变
 */
package com.worktime.pro.ui.motion

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * @param target 目标数值（分钟数、天数等原始整数）
 * @param format 把中间值渲染成展示文案，如「92.5h」「12天」
 */
@Composable
fun CountUpText(
    target: Int,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = FontWeight.Bold
) {
    val duration = motionDuration(Motion.BASE)
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = duration,
            easing = Motion.standardEasing
        ),
        label = "CountUpText"
    )
    Text(
        text = format(animated),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight
    )
}
