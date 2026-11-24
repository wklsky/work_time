/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderReliability.kt
 * @Description: 提醒可靠性检查与系统设置跳转：通知权限、电池优化、精确闹钟与自启动白名单
 */
package com.worktime.pro.domain.reminder

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 国内 ROM 清理后台时会连带取消闹钟、限制自启动，仅靠代码无法保活。
 * 这里集中提供状态查询与系统设置入口，由提醒设置页引导用户加入白名单。
 */
object ReminderReliability {

    fun notificationsEnabled(context: Context): Boolean =
        ReminderNotification.areNotificationsEnabled(context)

    /** 响铃渠道被用户单独屏蔽时通知不可见，也就没有「停止响铃」按钮 */
    fun alarmChannelBlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(ReminderNotification.ALARM_CHANNEL_ID)
            ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun ignoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean =
        ReminderScheduler.canScheduleExactAlarms(context)

    /** Android 14+ 全屏通知权限：未允许时锁屏只会显示普通通知，不会弹出停止界面 */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return manager.canUseFullScreenIntent()
    }

    fun fullScreenIntentSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 闹钟音量为 0 时响铃听不到，属于最常见的“没响”原因之一 */
    fun alarmVolumeSilent(context: Context): Boolean {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    fun soundSettingsIntent(): Intent =
        Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 直接申请忽略电池优化；ROM 不提供该入口时回落到电池优化列表 */
    fun batteryOptimizationIntent(context: Context): Intent {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolvable = runCatching {
            request.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
        return if (resolvable) {
            request
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /** API 31-32 需要用户手动授予精确闹钟；33+ 由 USE_EXACT_ALARM 自动授予 */
    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 各厂商自启动管理页差异很大，先尝试已知入口，全部不可用时退回应用详情页 */
    fun autoStartSettingsIntent(context: Context): Intent {
        for (candidate in OEM_AUTO_START) {
            val intent = Intent().apply {
                component = candidate
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolvable = runCatching {
                context.packageManager.resolveActivity(intent, 0) != null
            }.getOrDefault(false)
            if (resolvable) return intent
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun open(context: Context, intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)

    private val OEM_AUTO_START = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity"
        ),
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        ComponentName(
            "com.oplus.safecenter",
            "com.oplus.safecenter.permission.startup.StartupAppListActivity"
        ),
        ComponentName(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        ),
        ComponentName(
            "com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        ),
        ComponentName(
            "com.meizu.safe",
            "com.meizu.safe.permission.SmartBGActivity"
        ),
        ComponentName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
    )
}
