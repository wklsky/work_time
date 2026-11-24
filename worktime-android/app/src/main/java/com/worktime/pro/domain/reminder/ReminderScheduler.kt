/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderScheduler.kt
 * @Description: 用 AlarmManager 排定工时提醒与稍后提醒，应用退到后台后仍可触发
 */
package com.worktime.pro.domain.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.worktime.pro.MainActivity
import java.time.LocalDate

object ReminderScheduler {

    /** 主提醒与稍后提醒用不同 requestCode：主提醒重排/取消时不会误伤稍后提醒 */
    private const val REQUEST_CODE = 2101
    private const val SNOOZE_REQUEST_CODE = 2102

    const val EXTRA_TYPE = "extra_reminder_type"
    const val EXTRA_THRESHOLD = "extra_reminder_threshold"
    const val EXTRA_CURRENT = "extra_reminder_current"
    const val EXTRA_DATE = "extra_reminder_date"
    const val EXTRA_RING = "extra_reminder_ring"

    fun schedule(
        context: Context,
        triggerAtMillis: Long,
        type: ReminderType,
        thresholdMinutes: Int,
        currentMinutes: Int,
        date: LocalDate,
        ring: Boolean
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(
            context = context,
            requestCode = REQUEST_CODE,
            type = type,
            thresholdMinutes = thresholdMinutes,
            currentMinutes = currentMinutes,
            date = date,
            ring = ring
        )
        scheduleAlarm(
            manager = manager,
            context = context,
            triggerAtMillis = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000),
            pending = pending
        )
    }

    /**
     * 稍后提醒：独立 requestCode 的一次性闹钟。
     * 主提醒因「今日已提醒」被协调器取消时不会连带取消它，保证用户点的「稍后」一定生效。
     */
    fun scheduleSnooze(
        context: Context,
        type: ReminderType,
        thresholdMinutes: Int,
        currentMinutes: Int,
        ring: Boolean,
        delayMinutes: Int
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() +
            delayMinutes.coerceIn(1, 180) * 60_000L
        val pending = pendingIntent(
            context = context,
            requestCode = SNOOZE_REQUEST_CODE,
            type = type,
            thresholdMinutes = thresholdMinutes,
            currentMinutes = currentMinutes,
            date = LocalDate.now(),
            ring = ring
        )
        scheduleAlarm(manager, context, triggerAt, pending)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(
            context, REQUEST_CODE, ReminderType.OVERTIME, 0, 0, LocalDate.now(), false
        )
        manager.cancel(pending)
        pending.cancel()
    }

    fun cancelSnooze(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(
            context, SNOOZE_REQUEST_CODE, ReminderType.OVERTIME, 0, 0, LocalDate.now(), false
        )
        manager.cancel(pending)
        pending.cancel()
    }

    private fun scheduleAlarm(
        manager: AlarmManager,
        context: Context,
        triggerAtMillis: Long,
        pending: PendingIntent
    ) {
        val exact = canScheduleExact(manager)
        val scheduled = runCatching {
            if (exact) {
                // setAlarmClock 是系统眼中"用户闹钟"级别的精确闹钟：Doze 下也会准点唤醒，
                // 同时满足 Android 12+ 从后台启动前台服务（响铃）的豁免条件
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent(context)),
                    pending
                )
            } else {
                // 未获精确闹钟权限时退化为允许空闲唤醒，仍尽量准时
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pending
                )
            }
            true
        }.getOrDefault(false)
        if (scheduled) return

        // 个别 ROM 在精确闹钟可用性上自相矛盾（canScheduleExactAlarms 为 true 但 setAlarmClock 抛异常），
        // 先尝试精确兜底，仍失败就用不精确闹钟，宁可晚一点也不要丢失提醒
        val exactFallback = runCatching {
            if (exact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pending
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pending
                )
            }
            true
        }.getOrDefault(false)
        if (!exactFallback) {
            runCatching {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pending
                )
            }
        }
    }

    /** 当前系统是否允许精确闹钟；设置页据此提示用户授权 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    /** 状态栏闹钟图标被点击时打开应用 */
    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        type: ReminderType,
        thresholdMinutes: Int,
        currentMinutes: Int,
        date: LocalDate,
        ring: Boolean
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type.value)
            putExtra(EXTRA_THRESHOLD, thresholdMinutes)
            putExtra(EXTRA_CURRENT, currentMinutes)
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_RING, ring)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
