/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/motion/Motion.kt
 * @Description: 全项目统一动效令牌（时长/缓动/系统动画开关），一处定义避免各处散落 tween
 */
package com.worktime.pro.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 动效时长令牌（毫秒），语义化命名，禁止在页面里手写裸 tween 时长 */
object Motion {
    /** 纯状态翻转，无动画 */
    const val INSTANT = 0

    /** 按压回弹、色值切换、开关 */
    const val FAST = 120

    /** 页面内状态切换、计数过渡、淡入 */
    const val BASE = 200

    /** 切月滑动、弹层展开、删除收起 */
    const val SLOW = 320

    /** 打卡盖章、状态脉冲等强调时刻（全应用仅 1~2 处） */
    const val EMPHASIS = 480

    /** 位移/缩放用：快出慢入 */
    val standardEasing: Easing = FastOutSlowInEasing

    /** 淡入/淡出用：慢入渐出 */
    val fadeEasing: Easing = LinearOutSlowInEasing
}

/** 系统「移除动画」开启时返回 false，此时所有动效应降级为淡入或 0ms */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }
}

/** 按系统动画开关折算后的时长：关闭动画时归零，保证降级路径统一 */
@Composable
fun motionDuration(base: Int): Int = if (animationsEnabled()) base else 0
