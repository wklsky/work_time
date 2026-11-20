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
import java.time.LocalDate

object ReminderScheduler {

    private const val REQUEST_CODE = 2101

    const val EXTRA_TYPE = "extra_reminder_type"
    const val EXTRA_THRESHOLD = "extra_reminder_threshold"
    const val EXTRA_DATE = "extra_reminder_date"

    fun schedule(
        context: Context,
        triggerAtMillis: Long,
        type: ReminderType,
        thresholdMinutes: Int,
        date: LocalDate
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, type, thresholdMinutes, date)
        val triggerAt = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000)
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, ReminderType.OVERTIME, 0, LocalDate.now())
        manager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(
        context: Context,
        type: ReminderType,
        thresholdMinutes: Int,
        date: LocalDate
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type.value)
            putExtra(EXTRA_THRESHOLD, thresholdMinutes)
            putExtra(EXTRA_DATE, date.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
