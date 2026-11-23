/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/motion/Skeleton.kt
 * @Description: 加载骨架：低饱和呼吸块，替代 CircularProgressIndicator，避免「转圈→整屏跳变」
 */
package com.worktime.pro.ui.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/** 呼吸式骨架块，形状可传卡片/行/圆形 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}
