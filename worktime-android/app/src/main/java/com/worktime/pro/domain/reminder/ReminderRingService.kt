/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderRingService.kt
 * @Description: 提醒响铃前台服务，用闹钟音频流循环播放铃声并震动，到点后持续响铃直到用户停止
 */
package com.worktime.pro.domain.reminder

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class ReminderRingService : Service() {

    companion object {
        private const val ACTION_START = "com.worktime.pro.action.REMINDER_RING_START"
        private const val ACTION_STOP = "com.worktime.pro.action.REMINDER_RING_STOP"
        private const val EXTRA_TYPE = "extra_ring_type"
        private const val EXTRA_THRESHOLD = "extra_ring_threshold"
        private const val EXTRA_RINGTONE = "extra_ring_ringtone"

        /** 最长响铃时长，避免用户没注意到时一直响 */
        private const val RING_DURATION_MS = 60_000L

        fun start(
            context: Context,
            type: ReminderType,
            thresholdMinutes: Int,
            ringtoneUri: String
        ) {
            val intent = Intent(context, ReminderRingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TYPE, type.value)
                putExtra(EXTRA_THRESHOLD, thresholdMinutes)
                putExtra(EXTRA_RINGTONE, ringtoneUri)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReminderRingService::class.java))
        }
    }

    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val type = ReminderType.entries
            .firstOrNull { it.value == intent?.getStringExtra(EXTRA_TYPE) }
            ?: ReminderType.OVERTIME
        val threshold = intent?.getIntExtra(EXTRA_THRESHOLD, 0) ?: 0
        if (threshold <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 前台服务通知必须尽快发出，否则系统会判定启动超时
        val notification = ReminderNotification.alarmNotification(
            context = this,
            type = type,
            thresholdMinutes = threshold,
            stopIntent = stopIntent()
        )
        ServiceCompat.startForeground(
            this,
            ReminderNotification.ALARM_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
        val ringtoneUri = intent?.getStringExtra(EXTRA_RINGTONE).orEmpty()
        startRinging(ringtoneUri)
        return START_NOT_STICKY
    }

    private fun stopIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            0,
            Intent(this, ReminderRingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun startRinging(ringtoneUri: String) {
        acquireWakeLock()
        if (mediaPlayer == null) startPlayer(ringtoneUri)
        startVibration()
        stopHandler.removeCallbacks(stopRunnable)
        stopHandler.postDelayed(stopRunnable, RING_DURATION_MS)
    }

    /**
     * 优先播放用户选择的本地音频；文件被删除/权限失效时依次回退到系统默认闹钟、铃声、通知音。
     * USAGE_ALARM 让声音走闹钟音量通道，并与系统“允许闹钟”的免打扰策略保持一致。
     */
    private fun startPlayer(ringtoneUri: String) {
        val candidates = buildList {
            if (ringtoneUri.isNotBlank()) {
                runCatching { Uri.parse(ringtoneUri) }.getOrNull()?.let { add(it) }
            }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let { add(it) }
        }
        for (uri in candidates.distinct()) {
            if (tryStartPlayer(uri)) return
        }
    }

    private fun tryStartPlayer(uri: Uri): Boolean {
        val player = MediaPlayer()
        return runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(this@ReminderRingService, uri)
            player.isLooping = true
            player.prepare()
            player.start()
        }.fold(
            onSuccess = {
                mediaPlayer = player
                true
            },
            onFailure = {
                player.release()
                false
            }
        )
    }

    private fun startVibration() {
        vibrator = obtainVibrator()
        val pattern = longArrayOf(0, 800, 600)
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }.onFailure {
            vibrateFallback()
        }
    }

    /** 部分设备不支持自定义波形，退化为一次性默认震动 */
    @Suppress("DEPRECATION")
    private fun vibrateFallback() {
        runCatching { vibrator?.vibrate(500L) }
    }

    private fun obtainVibrator(): Vibrator? {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        return manager?.takeIf { it.hasVibrator() }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "worktime:reminderRing"
        ).apply {
            setReferenceCounted(false)
            acquire(RING_DURATION_MS + 5_000L)
        }
    }

    override fun onDestroy() {
        stopHandler.removeCallbacks(stopRunnable)
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
