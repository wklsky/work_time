/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderReceiver.kt
 * @Description: 提醒闹钟接收器，发送通知并记录当天已提醒标记
 */
package com.worktime.pro.domain.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.worktime.pro.data.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = ReminderType.entries
            .firstOrNull { it.value == intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) }
            ?: ReminderType.OVERTIME
        val threshold = intent.getIntExtra(ReminderScheduler.EXTRA_THRESHOLD, 0)
        if (threshold <= 0) return
        val current = intent.getIntExtra(ReminderScheduler.EXTRA_CURRENT, threshold)
            .coerceAtLeast(threshold)

        if (intent.getBooleanExtra(ReminderScheduler.EXTRA_RING, false)) {
            // 后台启动前台服务可能被系统拦截（未授予精确闹钟权限、ROM 限制自启动等），
            // 失败时退回带闹钟音的强提醒：提醒固定发声，不能因为降级变成静音
            val started = runCatching {
                ReminderRingService.start(
                    context = context,
                    type = type,
                    thresholdMinutes = threshold,
                    currentMinutes = current
                )
            }.isSuccess
            if (!started) {
                ReminderNotification.showLoud(context, type, threshold, current, true)
            }
        } else {
            ReminderNotification.show(context, type, threshold, current, false)
        }

        val dateKey = intent.getStringExtra(ReminderScheduler.EXTRA_DATE).orEmpty()
        val key = "$dateKey:${type.value}:$threshold"
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                SettingsDataStore(context.applicationContext).setLastReminderKey(key)
            }
            pendingResult.finish()
        }
    }
}
