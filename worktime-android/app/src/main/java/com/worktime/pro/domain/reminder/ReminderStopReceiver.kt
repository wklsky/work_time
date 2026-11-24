/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderStopReceiver.kt
 * @Description: 通知栏「停止响铃」广播接收器，服务被系统回收后也能兜底停止并撤掉通知
 */
package com.worktime.pro.domain.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderRingService.ACTION_STOP) return
        ReminderRingService.stop(context.applicationContext)
    }
}
