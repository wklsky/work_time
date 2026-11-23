/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/motion/Breathe.kt
 * @Description: 呼吸容器：对内容施加 0.94~1.06 的轻微缩放循环，用于空状态图标等需要「活」起来又不喧宾夺主的元素。
 *               系统关闭动画时缩放恒为 1，自动降级为静止。
 */
package com.worktime.pro.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun Breathe(
    modifier: Modifier = Modifier,
    minScale: Float = 0.94f,
    maxScale: Float = 1.06f,
    content: @Composable BoxScope.() -> Unit
) {
    val enabled = animationsEnabled()
    val transition = rememberInfiniteTransition(label = "breathe")
    val scale by transition.animateFloat(
        initialValue = if (enabled) minScale else 1f,
        targetValue = if (enabled) maxScale else 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breatheScale"
    )
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}
