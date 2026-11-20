/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/engine/PunchTimelineValidator.kt
 * @Description: 打卡时间线校验：上班与加班不可重叠，同类段必须闭合后才能开始下一段
 */
package com.worktime.pro.domain.engine

import com.worktime.pro.domain.model.PunchType

data class PunchMoment(
    val type: PunchType,
    val time: Long
)

object PunchTimelineValidator {

    /**
     * 按时间顺序模拟工作/加班状态机。
     * 规则：
     * - 上班段未结束时不能再次上班；
     * - 加班段未结束时不能再次加班；
     * - 上班段未结束时不能开始加班，加班段未结束时不能开始上班；
     * - 结束卡必须晚于对应的开始卡；没有任何开始卡的孤立结束卡会被忽略（兼容旧数据/分步补录）。
     * 同一时刻的结束卡优先于开始卡，允许“上一段结束的同一分钟开始下一段”。
     */
    fun validate(moments: List<PunchMoment>): String? {
        val sorted = moments.sortedWith(
            compareBy<PunchMoment> { it.time }.thenBy { priority(it.type) }
        )
        val hasWorkStart = moments.any { it.type == PunchType.WORK_START }
        val hasOtStart = moments.any { it.type == PunchType.OT_START }
        var workOpen = false
        var otOpen = false

        sorted.forEach { moment ->
            when (moment.type) {
                PunchType.WORK_START -> {
                    if (workOpen) return "上班时间段尚未结束，请先打下班卡"
                    if (otOpen) return "加班时间段尚未结束，不能开始上班"
                    workOpen = true
                }
                PunchType.WORK_END -> {
                    if (workOpen) {
                        workOpen = false
                    } else if (hasWorkStart) {
                        return "下班时间早于上班时间，或没有进行中的上班段"
                    }
                }
                PunchType.OT_START -> {
                    if (otOpen) return "上一段加班尚未结束，不能开始新的加班"
                    if (workOpen) return "上班时间段尚未结束，不能开始加班"
                    otOpen = true
                }
                PunchType.OT_END -> {
                    if (otOpen) {
                        otOpen = false
                    } else if (hasOtStart) {
                        return "加班退时间早于加班到时间，或没有进行中的加班段"
                    }
                }
            }
        }
        return null
    }

    private fun priority(type: PunchType): Int = when (type) {
        PunchType.WORK_END, PunchType.OT_END -> 0
        PunchType.WORK_START, PunchType.OT_START -> 1
    }
}
