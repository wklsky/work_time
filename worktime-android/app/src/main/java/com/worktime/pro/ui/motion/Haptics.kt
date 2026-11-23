/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/motion/Haptics.kt
 * @Description: 触觉反馈语义化封装：select=轻、confirm=中、reject=重，避免各页直接调底层 HapticFeedbackType
 */
package com.worktime.pro.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** 触觉分层：打卡/保存成功用 confirm，选择/开关用 select，删除/校验失败用 reject */
class Haptics(private val feedback: HapticFeedback) {
    /** 轻触：切换选项、点 Chip、选日期、开关 */
    fun select() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** 中等：打卡成功、保存成功、导出成功 */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** 较重：删除确认、写入被拒、校验失败 */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

/** 取当前平台的触觉反馈，供页面在 onClick 等回调里调用 */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
