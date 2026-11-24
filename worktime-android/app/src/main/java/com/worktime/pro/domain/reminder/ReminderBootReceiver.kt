/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderBootReceiver.kt
 * @Description: 开机/应用更新/系统时间变更后重新排定工时提醒，避免系统清空或移动闹钟后失效
 */
package com.worktime.pro.domain.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.WorkTimeDatabase
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != Intent.ACTION_TIME_CHANGED &&
            intent.action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val settings = SettingsDataStore(appContext).settings.first()
                if (!settings.reminderEnabled) {
                    ReminderScheduler.cancel(appContext)
                    ReminderScheduler.cancelSnooze(appContext)
                    return@runCatching
                }
                val today = LocalDate.now()
                val dateKey = TimeFormatter.formatDateKey(today)
                val database = WorkTimeDatabase.get(appContext)
                val events = database.punchEventDao().getByDateOnce(dateKey)
                val sessions = database.workSessionDao().getByDateOnce(dateKey)
                val plan = ReminderPlanner.plan(
                    settings = settings,
                    date = today,
                    events = events,
                    sessions = sessions,
                    now = LocalDateTime.now()
                )
                if (plan != null) {
                    ReminderScheduler.schedule(
                        context = appContext,
                        triggerAtMillis = plan.triggerAtMillis,
                        type = plan.type,
                        thresholdMinutes = plan.thresholdMinutes,
                        currentMinutes = plan.currentMinutes,
                        date = plan.date,
                        ring = plan.ring,
                        mode = plan.mode.value,
                        ringtoneUri = plan.ringtoneUri
                    )
                } else {
                    ReminderScheduler.cancel(appContext)
                }
            }
            pendingResult.finish()
        }
    }
}
