/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/motion/PulseDot.kt
 * @Description: 打卡成功脉冲点：日常只是一个状态点，pulseKey 变化时扩散一圈渐隐圆环，标识「刚留下一次记录」。
 *               圆环用 graphicsLayer 缩放，不改变布局尺寸，避免挤压相邻文字。
 */
package com.worktime.pro.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @param pulseKey 每次打卡成功自增一次，触发一次扩散；首次 0 不触发
 * @param dotSize 状态点直径，圆环在其上向外扩散 3.5 倍
 */
@Composable
fun PulseDot(
    color: Color,
    pulseKey: Int,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp
) {
    val ringScale = remember { Animatable(0f) }
    val ringAlpha = remember { Animatable(0f) }
    val duration = motionDuration(Motion.EMPHASIS)
    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            ringScale.snapTo(0f)
            ringAlpha.snapTo(0.45f)
            ringScale.animateTo(1f, tween(duration, easing = Motion.standardEasing))
            ringAlpha.animateTo(0f, tween(duration, easing = Motion.fadeEasing))
        }
    }
    Box(modifier = modifier.size(dotSize), contentAlignment = Alignment.Center) {
        // 扩散圆环：graphicsLayer 缩放不影响布局，环从点中心向外扩散并淡出
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f + ringScale.value * 3.5f
                    scaleX = s
                    scaleY = s
                    alpha = ringAlpha.value
                }
                .clip(CircleShape)
                .background(color)
        )
        // 中心状态点
        Box(
            modifier = Modifier.fillMaxSize().clip(CircleShape).background(color)
        )
    }
}
