/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderPlanner.kt
 * @Description: 纯提醒规划逻辑，供今日页、应用级协调器和开机接收器共用
 */
package com.worktime.pro.domain.reminder

import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.ReminderMode
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.util.TimeFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class ReminderPlan(
    val type: ReminderType,
    val thresholdMinutes: Int,
    val triggerAtMillis: Long,
    val date: LocalDate,
    /** true 时到点用闹钟音频流响铃，false 时只发普通通知 */
    val ring: Boolean,
    /** 响铃方式：声音+震动 / 仅震动 / 跟随系统 */
    val mode: ReminderMode,
    /** 自定义铃声 URI；空串表示系统默认闹钟铃声 */
    val ringtoneUri: String
)

object ReminderPlanner {

    /**
     * 计算是否需要排定提醒；返回 null 表示应取消当前闹钟。
     * 已达到阈值时立即提醒；未达到但有进行中的段时按剩余时长排定。
     */
    fun plan(
        settings: AppSettings,
        date: LocalDate,
        events: List<PunchEventEntity>,
        sessions: List<WorkSessionEntity>,
        now: LocalDateTime
    ): ReminderPlan? {
        if (!settings.reminderEnabled) return null
        val type = ReminderType.entries.firstOrNull { it.value == settings.reminderType }
            ?: return null
        val threshold = settings.reminderMinutes
        val key = "${TimeFormatter.formatDateKey(date)}:${type.value}:$threshold"
        if (settings.lastReminderKey == key) return null

        val totals = sessions.toDayTotals()
        fun latest(punchType: PunchType) = events.lastOrNull { it.punchType == punchType.name }
        val workStart = latest(PunchType.WORK_START)
        val workEnd = latest(PunchType.WORK_END)
        val otStart = latest(PunchType.OT_START)
        val otEnd = latest(PunchType.OT_END)
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val workOpen = workStart != null &&
            (workEnd == null || workStart.punchTime >= workEnd.punchTime)
        val otOpen = otStart != null &&
            (otEnd == null || otStart.punchTime >= otEnd.punchTime)
        // 进行中的段按毫秒精确计时：用分钟取整会让每次重排都把触发时刻往后推几十秒
        val openWorkMillis = if (workOpen) {
            (nowMillis - workStart!!.punchTime).coerceAtLeast(0L)
        } else 0L
        val openOtMillis = if (otOpen) {
            (nowMillis - otStart!!.punchTime).coerceAtLeast(0L)
        } else 0L

        val completedMinutes = when (type) {
            ReminderType.OVERTIME -> totals.overtimeMinutes
            ReminderType.WORK -> totals.regularMinutes
            ReminderType.NET -> totals.regularMinutes + totals.overtimeMinutes
        }
        val openMillis = when (type) {
            ReminderType.OVERTIME -> openOtMillis
            ReminderType.WORK -> openWorkMillis
            ReminderType.NET -> openWorkMillis + openOtMillis
        }
        val hasOpen = when (type) {
            ReminderType.OVERTIME -> otOpen
            ReminderType.WORK -> workOpen
            ReminderType.NET -> workOpen || otOpen
        }
        val thresholdMillis = threshold * 60_000L
        val targetMillis = completedMinutes * 60_000L + openMillis

        val triggerAt = when {
            targetMillis >= thresholdMillis -> nowMillis + 1_000
            hasOpen -> nowMillis + (thresholdMillis - targetMillis).coerceAtLeast(1_000L)
            else -> return null
        }
        return ReminderPlan(
            type = type,
            thresholdMinutes = threshold,
            triggerAtMillis = triggerAt,
            date = date,
            ring = settings.reminderRingEnabled,
            mode = ReminderMode.fromValue(settings.reminderMode),
            ringtoneUri = settings.reminderRingtoneUri
        )
    }
}
