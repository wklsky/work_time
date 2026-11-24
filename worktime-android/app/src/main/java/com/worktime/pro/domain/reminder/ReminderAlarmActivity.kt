/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderAlarmActivity.kt
 * @Description: 响铃全屏弹窗：锁屏/熄屏时展示提醒详情，提供停止响铃、稍后提醒与打开应用
 */
package com.worktime.pro.domain.reminder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worktime.pro.MainActivity
import com.worktime.pro.WorkTimeApplication
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.ReminderMode
import com.worktime.pro.ui.theme.WorkTimeTheme
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.delay

class ReminderAlarmActivity : ComponentActivity() {

    companion object {
        /** 统一构造弹窗 Intent，服务（全屏通知）与设置页「测试提醒」共用 */
        fun intent(
            context: Context,
            type: ReminderType,
            thresholdMinutes: Int,
            currentMinutes: Int,
            mode: String,
            ringtoneUri: String
        ): Intent = Intent(context, ReminderAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderScheduler.EXTRA_TYPE, type.value)
            putExtra(ReminderScheduler.EXTRA_THRESHOLD, thresholdMinutes)
            putExtra(ReminderScheduler.EXTRA_CURRENT, currentMinutes)
            putExtra(ReminderScheduler.EXTRA_MODE, mode)
            putExtra(ReminderScheduler.EXTRA_RINGTONE, ringtoneUri)
        }
    }

    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyAlarmWindowFlags()

        val type = ReminderType.entries
            .firstOrNull { it.value == intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) }
            ?: ReminderType.OVERTIME
        val threshold = intent.getIntExtra(ReminderScheduler.EXTRA_THRESHOLD, 0)
        if (threshold <= 0) {
            finish()
            return
        }
        val current = intent.getIntExtra(ReminderScheduler.EXTRA_CURRENT, threshold)
        val mode = intent.getStringExtra(ReminderScheduler.EXTRA_MODE)
            ?: ReminderMode.SOUND_VIBRATE.value
        val ringtoneUri = intent.getStringExtra(ReminderScheduler.EXTRA_RINGTONE).orEmpty()

        onBackPressedDispatcher.addCallback(this) {
            stopAndFinish()
        }

        val app = application as WorkTimeApplication
        setContent {
            val settings by app.settings.settings.collectAsState(initial = AppSettings())
            WorkTimeTheme(
                themeMode = settings.themeMode,
                customThemeEnabled = settings.customThemeEnabled,
                customLight = settings.customLightColors,
                customDark = settings.customDarkColors,
                fontScale = settings.fontScale
            ) {
                AlarmScreen(
                    type = type,
                    thresholdMinutes = threshold,
                    currentMinutes = current,
                    onStop = { stopAndFinish() },
                    onSnooze = { minutes ->
                        snoozeAndFinish(type, threshold, current, mode, ringtoneUri, minutes)
                    },
                    onOpenApp = { openAppAndFinish() },
                    onAutoFinish = { finishOnce() }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyAlarmWindowFlags() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun stopAndFinish() {
        if (finished) return
        finished = true
        ReminderRingService.stop(this)
        Toast.makeText(this, "已停止响铃", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun snoozeAndFinish(
        type: ReminderType,
        thresholdMinutes: Int,
        currentMinutes: Int,
        mode: String,
        ringtoneUri: String,
        delayMinutes: Int
    ) {
        if (finished) return
        finished = true
        ReminderScheduler.scheduleSnooze(
            context = this,
            type = type,
            thresholdMinutes = thresholdMinutes,
            currentMinutes = currentMinutes,
            ring = true,
            mode = mode,
            ringtoneUri = ringtoneUri,
            delayMinutes = delayMinutes
        )
        ReminderRingService.stop(this)
        Toast.makeText(this, "将在 $delayMinutes 分钟后再次提醒", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun openAppAndFinish() {
        if (finished) return
        finished = true
        ReminderRingService.stop(this)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        finish()
    }

    private fun finishOnce() {
        if (finished) return
        finished = true
        finish()
    }
}

@Composable
private fun AlarmScreen(
    type: ReminderType,
    thresholdMinutes: Int,
    currentMinutes: Int,
    onStop: () -> Unit,
    onSnooze: (Int) -> Unit,
    onOpenApp: () -> Unit,
    onAutoFinish: () -> Unit
) {
    var remainingSeconds by remember { mutableStateOf(0) }
    var ringActive by remember { mutableStateOf(ReminderRingService.isRinging()) }
    LaunchedEffect(Unit) {
        var sawRing = ringActive
        var missingTicks = 0
        while (true) {
            if (ReminderRingService.isRinging()) {
                sawRing = true
                ringActive = true
                missingTicks = 0
                val end = ReminderRingService.ringEndElapsedRealtime()
                remainingSeconds = ((end - SystemClock.elapsedRealtime()) / 1_000L)
                    .toInt().coerceAtLeast(0)
            } else {
                ringActive = false
                remainingSeconds = 0
                missingTicks++
                // 服务刚启动时给 3 秒宽限；响铃结束后 1 秒内自动关闭弹窗
                if ((sawRing && missingTicks >= 2) || (!sawRing && missingTicks >= 6)) {
                    onAutoFinish()
                    return@LaunchedEffect
                }
            }
            delay(500)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // 可滚动内容兜底横屏与大字体；内容不足一屏时仍纵向居中
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "工时提醒",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${type.label}累计已达到 ${TimeFormatter.formatDuration(thresholdMinutes)}",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                if (currentMinutes > thresholdMinutes) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "当前累计 ${TimeFormatter.formatDuration(currentMinutes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (ringActive) {
                        "提醒将在 $remainingSeconds 秒后自动停止"
                    } else {
                        "提醒已结束"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("停止响铃", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "稍后提醒",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(5, 10, 30).forEach { minutes ->
                        OutlinedButton(
                            onClick = { onSnooze(minutes) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$minutes 分钟")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onOpenApp) {
                    Text("打开应用")
                }
            }
        }
    }
}
