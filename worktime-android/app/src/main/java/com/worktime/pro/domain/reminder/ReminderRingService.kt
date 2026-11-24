/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderRingService.kt
 * @Description: 提醒响铃前台服务，跟随系统闹钟铃声与音量循环响铃并震动，直到用户停止
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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class ReminderRingService : Service() {

    companion object {
        private const val ACTION_START = "com.worktime.pro.action.REMINDER_RING_START"

        /**
         * 通知栏「停止响铃」通过广播触发，而不是 PendingIntent.getService。
         * 服务被 ROM 回收后通知可能残留，此时从后台启动服务会被系统拦截，
         * 广播接收器则始终能收到并兜底撤掉通知。
         */
        const val ACTION_STOP = "com.worktime.pro.action.REMINDER_RING_STOP"

        private const val EXTRA_TYPE = "extra_ring_type"
        private const val EXTRA_THRESHOLD = "extra_ring_threshold"
        private const val EXTRA_CURRENT = "extra_ring_current"

        /** 最长响铃时长，避免用户没注意到时一直响 */
        const val DEFAULT_RING_DURATION_MS = 60_000L

        /** 供全屏弹窗判断是否还在响铃、以及展示倒计时 */
        @Volatile
        private var ringEndElapsed = 0L

        fun isRinging(): Boolean = ringEndElapsed > SystemClock.elapsedRealtime()

        fun ringEndElapsedRealtime(): Long = ringEndElapsed

        fun start(
            context: Context,
            type: ReminderType,
            thresholdMinutes: Int,
            currentMinutes: Int
        ) {
            val intent = Intent(context, ReminderRingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TYPE, type.value)
                putExtra(EXTRA_THRESHOLD, thresholdMinutes)
                putExtra(EXTRA_CURRENT, currentMinutes)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** 停止响铃：先停服务，再兜底撤掉可能残留的通知（服务已被回收时通知仍在） */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, ReminderRingService::class.java))
            }
            ReminderNotification.cancelAlarm(context)
        }
    }

    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }
    private var mediaPlayer: MediaPlayer? = null
    private var stopAfterMs = DEFAULT_RING_DURATION_MS
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    /** 服务销毁后回调可能仍在队列里，用它阻止迟到的回调重新拉起播放 */
    private var destroyed = false
    /** 是否由本服务持有响铃通知；启动失败时发的是兜底通知，销毁时不能误删 */
    private var ownsAlarmNotification = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        destroyed = false
        // 兼容旧版本通知里残留的 ACTION_STOP
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
        val current = (intent?.getIntExtra(EXTRA_CURRENT, threshold) ?: threshold)
            .coerceAtLeast(threshold)
        stopAfterMs = DEFAULT_RING_DURATION_MS
        // 先标记响铃结束时间：全屏弹窗可能在通知发出的瞬间就被拉起
        ringEndElapsed = SystemClock.elapsedRealtime() + stopAfterMs

        // 前台服务通知必须尽快发出，否则系统会判定启动超时
        val foregroundStarted = runCatching {
            ServiceCompat.startForeground(
                this,
                ReminderNotification.ALARM_NOTIFICATION_ID,
                ReminderNotification.alarmNotification(
                    context = this,
                    type = type,
                    thresholdMinutes = threshold,
                    currentMinutes = current,
                    stopIntent = stopIntent(),
                    snoozeIntent = ReminderSnoozeReceiver.pendingIntent(
                        context = this,
                        type = type,
                        thresholdMinutes = threshold,
                        currentMinutes = current,
                        ring = true
                    ),
                    fullScreenIntent = alarmScreenIntent(type, threshold, current)
                ),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            )
            true
        }.getOrDefault(false)
        if (!foregroundStarted) {
            // 极少数 ROM 会拒绝前台服务：退回带闹钟音的普通通知，至少不会彻底静音。
            // 该通知交给系统自动取消，不能被下面的 onDestroy 一并清掉
            ownsAlarmNotification = false
            ringEndElapsed = 0L
            ReminderNotification.showLoud(this, type, threshold, current, true)
            stopSelf()
            return START_NOT_STICKY
        }
        ownsAlarmNotification = true

        startRinging()
        return START_NOT_STICKY
    }

    private fun stopIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, ReminderStopReceiver::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 锁屏/熄屏时直接弹出的停止界面 */
    private fun alarmScreenIntent(
        type: ReminderType,
        threshold: Int,
        current: Int
    ): PendingIntent =
        PendingIntent.getActivity(
            this,
            1,
            ReminderAlarmActivity.intent(
                context = this,
                type = type,
                thresholdMinutes = threshold,
                currentMinutes = current
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * 业务要求：提醒固定跟随系统闹钟，用闹钟音频流播放系统默认闹钟铃声并同时震动，
     * 不再提供「仅震动」或自定义音频。工时超标属于不能漏掉的提示，
     * 一旦允许静音或使用本地文件，静音模式与文件失效都会让提醒彻底无感。
     * 勿扰是否放行交给系统按 USAGE_ALARM 类别判定，应用不改写用户设定的静音策略。
     */
    private fun startRinging() {
        acquireWakeLock(stopAfterMs + 5_000L)
        // 先震动再准备音频：MediaPlayer 首次初始化有耗时，震动先给用户即时反馈
        startVibration()
        if (mediaPlayer == null) playFirstAvailable()
        stopHandler.removeCallbacks(stopRunnable)
        stopHandler.postDelayed(stopRunnable, stopAfterMs)
    }

    /** 系统默认闹钟音缺失时依次回退到默认铃声、通知音，避免整次提醒静音 */
    private fun ringCandidates(): List<Uri> = buildList {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { add(it) }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { add(it) }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let { add(it) }
    }.distinct()

    /** 按优先级异步尝试候选铃声；某个来源失效时自动换下一个 */
    private fun playFirstAvailable() {
        playCandidate(ringCandidates(), 0)
    }

    /**
     * 异步 prepare，避免在 Service 主线程阻塞；播放开始后开启循环，
     * 任一候选失败则继续尝试下一个，直到全部失败才静音。
     */
    private fun playCandidate(candidates: List<Uri>, index: Int) {
        if (destroyed) return
        val uri = candidates.getOrNull(index) ?: return
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        // 熄屏时保证音频不被系统挂起，和下面的 PARTIAL_WAKE_LOCK 一起兜底
        runCatching { player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK) }
        player.setOnPreparedListener { prepared ->
            // 准备期间用户已停止，或已被下一次响铃替换
            if (destroyed || mediaPlayer !== prepared) {
                runCatching { prepared.release() }
                return@setOnPreparedListener
            }
            prepared.isLooping = true
            runCatching { prepared.start() }
        }
        player.setOnCompletionListener { completed ->
            // 部分音频格式不支持原生循环，播放到结尾时手动重播，保证响铃持续
            if (!destroyed && mediaPlayer === completed) {
                runCatching { completed.start() }
            }
        }
        player.setOnErrorListener { failed, _, _ ->
            if (mediaPlayer === failed) mediaPlayer = null
            runCatching { failed.release() }
            if (!destroyed) playCandidate(candidates, index + 1)
            true
        }
        mediaPlayer = player
        runCatching {
            player.setDataSource(this, uri)
            player.prepareAsync()
        }.onFailure {
            if (mediaPlayer === player) mediaPlayer = null
            runCatching { player.release() }
            if (!destroyed) playCandidate(candidates, index + 1)
        }
    }

    private fun startVibration() {
        vibrator = obtainVibrator() ?: return
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

    private fun acquireWakeLock(timeoutMs: Long) {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        // 重复启动（同一时刻的重复闹钟）时刷新超时，避免锁提前过期
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "worktime:reminderRing"
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    override fun onDestroy() {
        destroyed = true
        ringEndElapsed = 0L
        stopHandler.removeCallbacks(stopRunnable)
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (ownsAlarmNotification) ReminderNotification.cancelAlarm(this)
        super.onDestroy()
    }
}
