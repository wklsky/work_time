/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderNotification.kt
 * @Description: 工时提醒通知，创建通知渠道并发送本地通知
 */
package com.worktime.pro.domain.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.worktime.pro.MainActivity
import com.worktime.pro.util.TimeFormatter

object ReminderNotification {

    private const val CHANNEL_ID = "worktime_reminder"
    private const val NOTIFICATION_ID = 2001

    /** 响铃模式专用渠道：声音/震动由 ReminderRingService 接管，渠道本身保持静音避免叠音 */
    const val ALARM_CHANNEL_ID = "worktime_reminder_alarm"
    const val ALARM_NOTIFICATION_ID = 2002

    /** 响铃服务被系统拦截时的兜底渠道：用闹钟音量响一次，避免完全静音 */
    private const val ALARM_SOUND_CHANNEL_ID = "worktime_reminder_alarm_sound"

    fun show(context: Context, type: ReminderType, thresholdMinutes: Int) {
        ensureChannel(context)
        if (!canNotify(context)) return

        val text = "${type.label}累计已达到 ${TimeFormatter.formatDuration(thresholdMinutes)}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("工时提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * 响铃无法启动时的兜底通知：仅响一声系统闹钟音。
     * 用于后台启动前台服务被 ROM 拦截、或前台服务启动失败的情况。
     */
    fun showLoud(context: Context, type: ReminderType, thresholdMinutes: Int) {
        ensureAlarmSoundChannel(context)
        if (!canNotify(context)) return

        val text = "${type.label}累计已达到 ${TimeFormatter.formatDuration(thresholdMinutes)}"
        val notification = NotificationCompat.Builder(context, ALARM_SOUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("工时提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, notification)
    }

    /** 响铃模式的前台服务通知：常驻、锁屏可见，带“停止响铃”按钮 */
    fun alarmNotification(
        context: Context,
        type: ReminderType,
        thresholdMinutes: Int,
        stopIntent: PendingIntent
    ): Notification {
        ensureAlarmChannel(context)
        val text = "${type.label}累计已达到 ${TimeFormatter.formatDuration(thresholdMinutes)}"
        return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("工时提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppIntent(context))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止响铃",
                stopIntent
            )
            .build()
    }

    /** 兜底清理：服务已被回收但响铃通知还留在通知栏时，用户仍能关掉它 */
    fun cancelAlarm(context: Context) {
        NotificationManagerCompat.from(context).cancel(ALARM_NOTIFICATION_ID)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "工时提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "累计工时达到设定阈值时提醒"
        }
        manager.createNotificationChannel(channel)
    }

    fun ensureAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "工时提醒（响铃）",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "累计工时达到设定阈值时用闹钟音量响铃并震动"
            // 铃声与震动由 ReminderRingService 精确控制（循环、可停止），渠道不再重复播放
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun ensureAlarmSoundChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ALARM_SOUND_CHANNEL_ID,
            "工时提醒（响铃兜底）",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "响铃服务受限时用闹钟音量提醒一次"
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attributes)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 通知权限是否可用；关闭时前台通知不可见，也就没有「停止响铃」入口 */
    fun areNotificationsEnabled(context: Context): Boolean = canNotify(context)

    private fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
