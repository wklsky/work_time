/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderCoordinator.kt
 * @Description: 应用级提醒协调器，不依赖当前页面，持续观察今日打卡并排定/取消闹钟
 */
package com.worktime.pro.domain.reminder

import android.content.Context
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.DateTicker
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderCoordinator(
    private val context: Context,
    private val repository: WorkTimeRepository,
    private val settings: SettingsDataStore
) {
    private var job: Job? = null

    private data class Snapshot(
        val settings: AppSettings,
        val date: LocalDate,
        val events: List<PunchEventEntity>,
        val sessions: List<WorkSessionEntity>
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            combine(settings.settings, DateTicker.dates()) { appSettings, date ->
                appSettings to date
            }.flatMapLatest { (appSettings, date) ->
                val dateKey = TimeFormatter.formatDateKey(date)
                combine(
                    repository.observeEvents(dateKey),
                    repository.observeSessions(dateKey)
                ) { events, sessions ->
                    Snapshot(appSettings, date, events, sessions)
                }
            }.collect { snapshot ->
                val plan = ReminderPlanner.plan(
                    settings = snapshot.settings,
                    date = snapshot.date,
                    events = snapshot.events,
                    sessions = snapshot.sessions,
                    now = LocalDateTime.now()
                )
                if (plan != null) {
                    ReminderScheduler.schedule(
                        context = context,
                        triggerAtMillis = plan.triggerAtMillis,
                        type = plan.type,
                        thresholdMinutes = plan.thresholdMinutes,
                        date = plan.date,
                        ring = plan.ring,
                        mode = plan.mode.value,
                        ringtoneUri = plan.ringtoneUri
                    )
                } else {
                    ReminderScheduler.cancel(context)
                }
            }
        }
    }
}
