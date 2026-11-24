/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderSnoozeReceiver.kt
 * @Description: 通知栏「稍后提醒」广播接收器，停止本次响铃并排定一次性稍后闹钟
 */
package com.worktime.pro.domain.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.worktime.pro.data.datastore.ReminderMode

class ReminderSnoozeReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.worktime.pro.action.REMINDER_SNOOZE"
        const val EXTRA_DELAY_MINUTES = "extra_snooze_delay_minutes"
        const val DEFAULT_DELAY_MINUTES = 10
        private const val REQUEST_CODE = 3101

        fun pendingIntent(
            context: Context,
            type: ReminderType,
            thresholdMinutes: Int,
            currentMinutes: Int,
            ring: Boolean,
            mode: String,
            ringtoneUri: String,
            delayMinutes: Int = DEFAULT_DELAY_MINUTES
        ): PendingIntent {
            val intent = Intent(context, ReminderSnoozeReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(ReminderScheduler.EXTRA_TYPE, type.value)
                putExtra(ReminderScheduler.EXTRA_THRESHOLD, thresholdMinutes)
                putExtra(ReminderScheduler.EXTRA_CURRENT, currentMinutes)
                putExtra(ReminderScheduler.EXTRA_RING, ring)
                putExtra(ReminderScheduler.EXTRA_MODE, mode)
                putExtra(ReminderScheduler.EXTRA_RINGTONE, ringtoneUri)
                putExtra(EXTRA_DELAY_MINUTES, delayMinutes)
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE) return
        val type = ReminderType.entries
            .firstOrNull { it.value == intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) }
            ?: ReminderType.OVERTIME
        val threshold = intent.getIntExtra(ReminderScheduler.EXTRA_THRESHOLD, 0)
        if (threshold <= 0) return
        val current = intent.getIntExtra(ReminderScheduler.EXTRA_CURRENT, threshold)
        val ring = intent.getBooleanExtra(ReminderScheduler.EXTRA_RING, true)
        val mode = intent.getStringExtra(ReminderScheduler.EXTRA_MODE)
            ?: ReminderMode.SOUND_VIBRATE.value
        val ringtone = intent.getStringExtra(ReminderScheduler.EXTRA_RINGTONE).orEmpty()
        val delay = intent.getIntExtra(EXTRA_DELAY_MINUTES, DEFAULT_DELAY_MINUTES)
            .coerceIn(1, 180)

        ReminderScheduler.scheduleSnooze(
            context = context.applicationContext,
            type = type,
            thresholdMinutes = threshold,
            currentMinutes = current,
            ring = ring,
            mode = mode,
            ringtoneUri = ringtone,
            delayMinutes = delay
        )
        // 稍后提醒会重新响铃，先把当前这次停掉并清掉原通知
        ReminderRingService.stop(context.applicationContext)
        ReminderNotification.cancelReminder(context.applicationContext)
    }
}
