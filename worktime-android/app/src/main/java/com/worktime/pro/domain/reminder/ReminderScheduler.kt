/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderScheduler.kt
 * @Description: 用 AlarmManager 排定工时提醒，应用退到后台后仍可触发
 */
package com.worktime.pro.domain.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.worktime.pro.MainActivity
import com.worktime.pro.data.datastore.ReminderMode
import java.time.LocalDate

object ReminderScheduler {

    private const val REQUEST_CODE = 2101

    const val EXTRA_TYPE = "extra_reminder_type"
    const val EXTRA_THRESHOLD = "extra_reminder_threshold"
    const val EXTRA_DATE = "extra_reminder_date"
    const val EXTRA_RING = "extra_reminder_ring"
    const val EXTRA_RINGTONE = "extra_reminder_ringtone"
    const val EXTRA_MODE = "extra_reminder_mode"

    fun schedule(
        context: Context,
        triggerAtMillis: Long,
        type: ReminderType,
        thresholdMinutes: Int,
        date: LocalDate,
        ring: Boolean,
        mode: String,
        ringtoneUri: String
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, type, thresholdMinutes, date, ring, mode, ringtoneUri)
        val triggerAt = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000)
        val exact = canScheduleExact(manager)
        val scheduled = runCatching {
            if (exact) {
                // setAlarmClock 是系统眼中"用户闹钟"级别的精确闹钟：Doze 下也会准点唤醒，
                // 同时满足 Android 12+ 从后台启动前台服务（响铃）的豁免条件
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent(context)),
                    pending
                )
            } else {
                // 未获精确闹钟权限时退化为允许空闲唤醒，仍尽量准时
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            true
        }.getOrDefault(false)
        if (scheduled) return

        // 个别 ROM 在精确闹钟可用性上自相矛盾（canScheduleExactAlarms 为 true 但 setAlarmClock 抛异常），
        // 先尝试精确兜底，仍失败就用不精确闹钟，宁可晚一点也不要丢失提醒
        val exactFallback = runCatching {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            true
        }.getOrDefault(false)
        if (!exactFallback) {
            runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        // 取消只比对组件与 requestCode，附加参数用默认值即可
        val pending = pendingIntent(
            context, ReminderType.OVERTIME, 0, LocalDate.now(), false,
            ReminderMode.SOUND_VIBRATE.value, ""
        )
        manager.cancel(pending)
        pending.cancel()
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
        type: ReminderType,
        thresholdMinutes: Int,
        date: LocalDate,
        ring: Boolean,
        mode: String,
        ringtoneUri: String
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type.value)
            putExtra(EXTRA_THRESHOLD, thresholdMinutes)
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_RING, ring)
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_RINGTONE, ringtoneUri)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
