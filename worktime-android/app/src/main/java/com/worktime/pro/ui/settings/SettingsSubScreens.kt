/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsSubScreens.kt
 * @Description: 设置二级页面，按分组拆分为打卡、显示、提醒、状态映射与数据管理五个独立页面
 */
package com.worktime.pro.ui.settings

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.ClockDialStyle
import com.worktime.pro.data.datastore.ReminderMode
import com.worktime.pro.data.datastore.ThemeMode
import com.worktime.pro.data.datastore.TimeFormatMode
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.reminder.ReminderAlarmActivity
import com.worktime.pro.domain.reminder.ReminderReliability
import com.worktime.pro.domain.reminder.ReminderRingService
import com.worktime.pro.domain.reminder.ReminderType
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.theme.CustomPalette
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/** 打卡设置：默认动作以及工作规则与习惯打卡两个三级入口 */
@Composable
fun SettingsPunchScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenHabits: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val currentDefaultPunch = PunchType.fromName(settings.defaultPunchType)
        ?: PunchType.WORK_START

    SettingsPage(
        title = "打卡设置",
        subtitle = "默认动作与规则管理",
        onBack = onBack
    ) {
        SettingsGroupCard {
            ChipBlock(
                title = "默认打卡动作",
                description = "今日页上班/加班都可选时，默认选中这里设定的动作；下班、加班退仍按当前状态自动切换。"
            ) {
                FilterChip(
                    selected = currentDefaultPunch == PunchType.WORK_START,
                    onClick = { viewModel.setDefaultPunchType(PunchType.WORK_START) },
                    label = { Text("上班") }
                )
                FilterChip(
                    selected = currentDefaultPunch == PunchType.OT_START,
                    onClick = { viewModel.setDefaultPunchType(PunchType.OT_START) },
                    label = { Text("加班到") }
                )
            }
        }

        SettingsGroupCard {
            LinkRow(
                title = "工作规则",
                description = "上下班、午休、加班缓冲、日切与工作日",
                onClick = onOpenRules
            )
            ItemDivider()
            LinkRow(
                title = "习惯打卡",
                description = "管理固定班次时间，首页可一键应用",
                onClick = onOpenHabits
            )
        }
    }
}

/** 显示与外观：主题、自定义配色、时间制式与日历展示 */
@Composable
fun SettingsDisplayScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit,
    onOpenCustomTheme: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val currentTheme = ThemeMode.entries.firstOrNull { it.value == settings.themeMode } ?: ThemeMode.SYSTEM
    val currentTimeFormat = TimeFormatMode.entries.firstOrNull { it.value == settings.timeFormat }
        ?: TimeFormatMode.SYSTEM
    val currentMode = CalendarDisplayMode.entries.firstOrNull { it.value == settings.calendarDisplayMode }
        ?: CalendarDisplayMode.WORK_TIME
    val currentDialStyle = ClockDialStyle.fromValue(settings.clockDialStyle)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SettingsPage(
        title = "显示与外观",
        subtitle = "主题、时间制式与日历展示",
        onBack = onBack
    ) {
        SettingsGroupCard {
            ChipBlock(title = "主题") {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = currentTheme == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            ItemDivider()
            SettingRow(
                title = "自定义配色",
                description = when {
                    currentTheme == ThemeMode.BLACK_WHITE && settings.customThemeEnabled ->
                        "已启用，但黑白模式不使用自定义配色"
                    settings.customThemeEnabled ->
                        "已启用 · 品牌色、工时色、加班色与文字颜色"
                    else ->
                        "未启用 · 修改颜色会自动启用"
                },
                descriptionColor = when {
                    currentTheme == ThemeMode.BLACK_WHITE && settings.customThemeEnabled ->
                        MaterialTheme.colorScheme.tertiary
                    settings.customThemeEnabled -> MaterialTheme.colorScheme.secondary
                    else -> muted
                },
                trailing = {
                    val editingDark = settings.themeMode == ThemeMode.DARK.value ||
                        (settings.themeMode == ThemeMode.SYSTEM.value && isSystemInDarkTheme())
                    val previewPalette = CustomPalette.deserialize(
                        if (editingDark) settings.customDarkColors else settings.customLightColors
                    ) ?: if (editingDark) {
                        CustomPalette.defaultDark()
                    } else {
                        CustomPalette.defaultLight()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                previewPalette.primary,
                                previewPalette.work,
                                previewPalette.overtime
                            ).forEach { color -> ColorDot(color) }
                        }
                        TextButton(onClick = onOpenCustomTheme) { Text("管理") }
                    }
                }
            )
        }

        SettingsGroupCard {
            ChipBlock(title = "时间制式") {
                FilterChip(
                    selected = currentTimeFormat == TimeFormatMode.SYSTEM,
                    onClick = { viewModel.setTimeFormat(TimeFormatMode.SYSTEM) },
                    label = { Text("跟随系统") }
                )
                FilterChip(
                    selected = currentTimeFormat == TimeFormatMode.HOUR_24,
                    onClick = { viewModel.setTimeFormat(TimeFormatMode.HOUR_24) },
                    label = { Text("24 小时") }
                )
                FilterChip(
                    selected = currentTimeFormat == TimeFormatMode.HOUR_12,
                    onClick = { viewModel.setTimeFormat(TimeFormatMode.HOUR_12) },
                    label = { Text("12 小时") }
                )
            }
            ItemDivider()
            ChipBlock(
                title = "表盘数字样式",
                description = "时间选择器表盘的刻度风格；时间固定 12 小时制，用上午/下午区分。"
            ) {
                ClockDialStyle.entries.forEach { style ->
                    FilterChip(
                        selected = currentDialStyle == style,
                        onClick = { viewModel.setClockDialStyle(style) },
                        label = { Text(style.label) }
                    )
                }
            }
        }

        SettingsGroupCard {
            ChipBlock(
                title = "日历每日显示内容",
                description = "全局统一生效：可选择在日历中显示全部、仅上班时长或仅加班时长。"
            ) {
                CalendarDisplayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = currentMode == mode,
                        onClick = { viewModel.setCalendarDisplayMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            ItemDivider()
            SwitchRow(
                title = "加班色阶",
                description = "日历中按加班时长显示底色深浅",
                checked = settings.showOvertimeShade,
                onCheckedChange = viewModel::setOvertimeShade
            )
        }
    }
}

/** 提醒：开关、类型与阈值。关闭后类型与阈值自动收起，避免留下无效控件 */
@Composable
fun SettingsReminderScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val currentReminderType = ReminderType.entries
        .firstOrNull { it.value == settings.reminderType }
        ?: ReminderType.OVERTIME

    // 系统设置状态：用户从系统设置页返回时在 ON_RESUME 重新读取，避免显示过期状态
    var notificationsEnabled by remember {
        mutableStateOf(ReminderReliability.notificationsEnabled(context))
    }
    var alarmChannelBlocked by remember {
        mutableStateOf(ReminderReliability.alarmChannelBlocked(context))
    }
    var batteryUnrestricted by remember {
        mutableStateOf(ReminderReliability.ignoringBatteryOptimizations(context))
    }
    var exactAlarmAllowed by remember {
        mutableStateOf(ReminderReliability.canScheduleExactAlarms(context))
    }
    var fullScreenAllowed by remember {
        mutableStateOf(ReminderReliability.canUseFullScreenIntent(context))
    }
    var alarmVolumeSilent by remember {
        mutableStateOf(ReminderReliability.alarmVolumeSilent(context))
    }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var showNotificationDenied by remember { mutableStateOf(false) }
    // Compose 内容宿主最终是 Activity；层层拆开 ContextWrapper，避免主题包装导致取不到
    val lifecycleOwner = context.findActivityLifecycleOwner()
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    notificationsEnabled = ReminderReliability.notificationsEnabled(context)
                    alarmChannelBlocked = ReminderReliability.alarmChannelBlocked(context)
                    batteryUnrestricted =
                        ReminderReliability.ignoringBatteryOptimizations(context)
                    exactAlarmAllowed = ReminderReliability.canScheduleExactAlarms(context)
                    fullScreenAllowed = ReminderReliability.canUseFullScreenIntent(context)
                    alarmVolumeSilent = ReminderReliability.alarmVolumeSilent(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val notificationsReady = notificationsEnabled && !alarmChannelBlocked
    // Android 13+ 且系统权限尚未授予时还能弹系统授权框；否则只能去通知设置里改
    val canRequestNotificationPermission = Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    val openSystemPage: (Intent) -> Unit = { intent ->
        if (!ReminderReliability.open(context, intent)) {
            Toast.makeText(
                context,
                "当前系统没有对应设置页，请到系统设置中手动开启",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val fixNotifications: () -> Unit = {
        if (!notificationsEnabled && canRequestNotificationPermission) {
            showNotificationRationale = true
        } else {
            openSystemPage(ReminderReliability.notificationSettingsIntent(context))
        }
    }
    // 31-32 必须手动授权；33+ 正常由 USE_EXACT_ALARM 自动授予，只有异常未授予时才提示
    val showExactAlarmRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 || !exactAlarmAllowed)
    val showFullScreenRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    val todayReminderKey =
        "${TimeFormatter.formatDateKey(LocalDate.now())}:${currentReminderType.value}:" +
            settings.reminderMinutes
    val remindedToday = settings.lastReminderKey == todayReminderKey

    // Android 13+ 发送提醒需要通知权限
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationsEnabled = true
            viewModel.setReminderEnabled(true)
        } else {
            showNotificationDenied = true
        }
    }

    // 从本地文件里选择铃声；拿持久化读权限，重启后仍能播放
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val name = queryDisplayName(context, uri)
            if (settings.reminderRingtoneUri.isNotBlank() &&
                settings.reminderRingtoneUri != uri.toString()
            ) {
                releaseRingtonePermission(context, settings.reminderRingtoneUri)
            }
            viewModel.setReminderRingtone(uri.toString(), name)
            Toast.makeText(context, "已选择铃声：$name", Toast.LENGTH_SHORT).show()
        }
    }

    // 三选项里只有「仅震动」不需要铃声，用它控制铃声行的显示
    val currentMode = ReminderMode.fromValue(settings.reminderMode)

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text("开启通知权限") },
            text = {
                Text(
                    "工时提醒需要通知权限：达到阈值后才能发送提醒；" +
                        "响铃提醒还需要通知栏里的「停止响铃」与「稍后提醒」。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                ) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) { Text("暂不") }
            }
        )
    }
    if (showNotificationDenied) {
        AlertDialog(
            onDismissRequest = { showNotificationDenied = false },
            title = { Text("通知权限未开启") },
            text = {
                Text(
                    "当前无法发送提醒通知，响铃时通知栏也没有停止按钮。" +
                        "请在系统设置中允许本应用发送通知。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationDenied = false
                        openSystemPage(ReminderReliability.notificationSettingsIntent(context))
                    }
                ) { Text("去系统设置") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDenied = false }) { Text("取消") }
            }
        )
    }

    SettingsPage(
        title = "提醒",
        subtitle = "累计工时达标后提醒，可选响铃、仅震动或跟随系统",
        onBack = onBack
    ) {
        // 已开启提醒但通知不可用时，置顶提示，避免用户以为提醒失效
        AnimatedVisibility(visible = settings.reminderEnabled && !notificationsReady) {
            NotificationPermissionBanner(
                channelBlocked = notificationsEnabled && alarmChannelBlocked,
                onClick = fixNotifications
            )
        }
        SettingsGroupCard {
            SwitchRow(
                title = "累计提醒",
                description = "达到设定时长后提醒，可在下方开启响铃；后台权限不足时会延迟",
                checked = settings.reminderEnabled,
                onCheckedChange = { enabled ->
                    when {
                        !enabled -> viewModel.setReminderEnabled(false)
                        notificationsEnabled -> viewModel.setReminderEnabled(true)
                        canRequestNotificationPermission -> showNotificationRationale = true
                        else -> showNotificationDenied = true
                    }
                }
            )
            AnimatedVisibility(visible = settings.reminderEnabled) {
                Column {
                    ItemDivider()
                    ChipBlock(title = "提醒类型") {
                        ReminderType.entries.forEach { type ->
                            FilterChip(
                                selected = settings.reminderType == type.value,
                                onClick = { viewModel.setReminderType(type) },
                                label = { Text(type.label) }
                            )
                        }
                    }
                    ReminderThreshold(settings = settings, onCommit = viewModel::setReminderMinutes)
                    Text(
                        if (remindedToday) {
                            "今日已提醒 · 修改类型或阈值可再次提醒"
                        } else {
                            "今日尚未提醒 · 达到阈值后自动触发"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (remindedToday) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            muted
                        }
                    )
                    ItemDivider()
                    SwitchRow(
                        title = "响铃提醒",
                        description = "到点持续提醒最多 60 秒；可在通知里停止，声音与震动方式见下方",
                        checked = settings.reminderRingEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setReminderRingEnabled(enabled)
                            if (enabled && !notificationsReady) {
                                Toast.makeText(
                                    context,
                                    "通知权限未开启，响铃时通知栏没有停止按钮",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                    AnimatedVisibility(visible = settings.reminderRingEnabled) {
                        Column {
                            ItemDivider()
                            ChipBlock(title = "提醒方式") {
                                ReminderMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.reminderMode == mode.value,
                                        onClick = { viewModel.setReminderMode(mode) },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                            Text(
                                currentMode.summary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // 仅震动模式不发声，铃声选择对用户无意义，直接隐藏避免误解
                            AnimatedVisibility(visible = currentMode != ReminderMode.VIBRATE_ONLY) {
                                Column {
                                    ItemDivider()
                                    SettingRow(
                                        title = "提醒铃声",
                                        description = if (settings.reminderRingtoneUri.isBlank()) {
                                            "系统默认闹钟 · 点击选择本地音频"
                                        } else {
                                            "${settings.reminderRingtoneName.ifBlank { "本地音频" }} · 点击更换"
                                        },
                                        onClick = { ringtoneLauncher.launch(arrayOf("audio/*")) },
                                        trailing = {
                                            if (settings.reminderRingtoneUri.isNotBlank()) {
                                                TextButton(
                                                    onClick = {
                                                        releaseRingtonePermission(
                                                            context,
                                                            settings.reminderRingtoneUri
                                                        )
                                                        viewModel.clearReminderRingtone()
                                                    }
                                                ) {
                                                    Text("恢复默认")
                                                }
                                            } else {
                                                TextButton(
                                                    onClick = {
                                                        ringtoneLauncher.launch(arrayOf("audio/*"))
                                                    }
                                                ) {
                                                    Text("选择")
                                                }
                                            }
                                        }
                                    )
                                    Text(
                                        "支持选择手机里的音频文件；文件被删除或换机恢复备份后会回退到系统默认铃声。",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 提醒可靠性：国内 ROM 清理后台会取消闹钟/限制自启动，必须引导用户逐项放行
        AnimatedVisibility(visible = settings.reminderEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("提醒可靠性")
                SettingsGroupCard {
                    Text(
                        "部分手机清理后台时会取消提醒。以下项目全部允许后，退到后台也能准点响铃。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                    SettingRow(
                        title = "通知权限",
                        description = when {
                            !notificationsEnabled -> "已关闭，提醒无法送达，通知栏也没有停止按钮"
                            alarmChannelBlocked -> "响铃通知渠道被单独屏蔽，通知栏没有停止按钮"
                            else -> "已开启，响铃时可从通知栏停止或稍后提醒"
                        },
                        descriptionColor = if (notificationsReady) {
                            muted
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        trailing = {
                            TextButton(onClick = fixNotifications) {
                                Text(if (notificationsReady) "查看" else "去开启")
                            }
                        }
                    )
                    ItemDivider()
                    SettingRow(
                        title = "后台运行",
                        description = if (batteryUnrestricted) {
                            "不受电池优化限制"
                        } else {
                            "受电池优化限制，清理后台后闹钟可能失效"
                        },
                        descriptionColor = if (batteryUnrestricted) {
                            muted
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        trailing = {
                            TextButton(
                                onClick = {
                                    openSystemPage(
                                        ReminderReliability.batteryOptimizationIntent(context)
                                    )
                                }
                            ) {
                                Text(if (batteryUnrestricted) "查看" else "去设置")
                            }
                        }
                    )
                    if (showExactAlarmRow) {
                        ItemDivider()
                        SettingRow(
                            title = "精确闹钟",
                            description = if (exactAlarmAllowed) {
                                "已允许，到点准点触发"
                            } else {
                                "未允许，系统可能延迟十几分钟"
                            },
                            descriptionColor = if (exactAlarmAllowed) {
                                muted
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            trailing = {
                                TextButton(
                                    onClick = {
                                        ReminderReliability.exactAlarmSettingsIntent(context)
                                            ?.let(openSystemPage)
                                    }
                                ) {
                                    Text(if (exactAlarmAllowed) "查看" else "去开启")
                                }
                            }
                        )
                    }
                    if (showFullScreenRow) {
                        ItemDivider()
                        SettingRow(
                            title = "闹钟弹窗",
                            description = if (fullScreenAllowed) {
                                "已允许，锁屏或熄屏时会弹出停止界面"
                            } else {
                                "未允许，锁屏时只显示通知，不会弹出停止界面"
                            },
                            descriptionColor = if (fullScreenAllowed) {
                                muted
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            trailing = {
                                TextButton(
                                    onClick = {
                                        ReminderReliability.fullScreenIntentSettingsIntent(context)
                                            ?.let(openSystemPage)
                                    }
                                ) {
                                    Text(if (fullScreenAllowed) "查看" else "去开启")
                                }
                            }
                        )
                    }
                    if (alarmVolumeSilent) {
                        ItemDivider()
                        SettingRow(
                            title = "闹钟音量",
                            description = "当前为静音，响铃会听不到声音",
                            descriptionColor = MaterialTheme.colorScheme.error,
                            trailing = {
                                TextButton(
                                    onClick = {
                                        openSystemPage(ReminderReliability.soundSettingsIntent())
                                    }
                                ) { Text("去设置") }
                            }
                        )
                    }
                    ItemDivider()
                    SettingRow(
                        title = "自启动白名单",
                        description = "部分手机清理后台后需允许自启动才能继续提醒",
                        trailing = {
                            TextButton(
                                onClick = {
                                    openSystemPage(
                                        ReminderReliability.autoStartSettingsIntent(context)
                                    )
                                }
                            ) { Text("去设置") }
                        }
                    )
                    ItemDivider()
                    SettingRow(
                        title = "测试提醒",
                        description = "按当前提醒方式试一次（10 秒），并预览闹铃弹窗与通知栏操作",
                        trailing = {
                            TextButton(
                                onClick = {
                                    val started = runCatching {
                                        ReminderRingService.start(
                                            context = context,
                                            type = currentReminderType,
                                            thresholdMinutes = settings.reminderMinutes,
                                            currentMinutes = settings.reminderMinutes,
                                            ringtoneUri = settings.reminderRingtoneUri,
                                            mode = settings.reminderMode,
                                            durationMs = 10_000L
                                        )
                                    }.isSuccess
                                    if (started) {
                                        runCatching {
                                            context.startActivity(
                                                ReminderAlarmActivity.intent(
                                                    context = context,
                                                    type = currentReminderType,
                                                    thresholdMinutes = settings.reminderMinutes,
                                                    currentMinutes = settings.reminderMinutes,
                                                    mode = settings.reminderMode,
                                                    ringtoneUri = settings.reminderRingtoneUri
                                                )
                                            )
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "响铃启动失败，请检查通知与后台权限",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            ) { Text("试响") }
                        }
                    )
                }
            }
        }
    }
}

/** 提醒已开启但通知不可用时的置顶提示，点按钮直接申请权限或跳系统设置 */
@Composable
private fun NotificationPermissionBanner(
    channelBlocked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (channelBlocked) "响铃通知被系统屏蔽" else "通知权限未开启",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    if (channelBlocked) {
                        "通知栏不会显示「停止响铃」和「稍后提醒」"
                    } else {
                        "提醒无法送达，响铃时也无法在通知栏停止"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onClick) {
                Text(
                    if (channelBlocked) "去设置" else "去开启",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/** 沿 ContextWrapper 链找到宿主 Activity 的 LifecycleOwner，供页面监听 ON_RESUME */
private fun Context.findActivityLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

/** 释放不再使用的本地音频读取授权，避免持久化授权越积越多 */
private fun releaseRingtonePermission(context: Context, uriString: String) {
    if (uriString.isBlank()) return
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(uriString),
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

/** 读取 SAF 文件的显示名，取不到时退回 URI 末段 */
private fun queryDisplayName(context: Context, uri: Uri): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    val name = runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "本地音频"
}

/** 阈值滑块：拖动过程中只更新本地状态，松手才写库，避免每一帧都触发 DataStore 写入 */
@Composable
private fun ReminderThreshold(
    settings: AppSettings,
    onCommit: (Int) -> Unit
) {
    var reminderValue by remember(settings.reminderMinutes) {
        mutableFloatStateOf(settings.reminderMinutes.toFloat())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("提醒阈值", modifier = Modifier.weight(1f))
        Text(
            TimeFormatter.formatDuration((reminderValue / 30f).roundToInt() * 30),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
    Slider(
        value = reminderValue,
        onValueChange = { reminderValue = it },
        onValueChangeFinished = { onCommit((reminderValue / 30f).roundToInt() * 30) },
        valueRange = 60f..720f,
        steps = 21,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Text(
        "例如加班满 7 小时提醒；达到阈值提醒一次，修改类型或阈值后可再次提醒。",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 数据管理：导出与导入分开两卡，危险操作独立成区并用 error 色提示 */
@Composable
fun SettingsDataScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val backupViewModel: BackupViewModel = viewModel(factory = factory)
    val backupMessage by backupViewModel.message.collectAsState()
    val pending by backupViewModel.pending.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 导出保存位置选择器：用系统文件选择器让用户选保存位置，模拟器可用，导出后可直接在文件管理器查看
    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = backupViewModel.export()
                result.onSuccess { file ->
                    val saved = runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                file.inputStream().use { it.copyTo(out) }
                            } ?: error("无法写入所选位置")
                        }
                    }
                    Toast.makeText(
                        context,
                        if (saved.isSuccess) "备份已保存到所选位置" else "保存失败，请重试",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    // CSV 明细导出：供用户用表格软件打开观看
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = backupViewModel.exportCsv()
                result.onSuccess { csv ->
                    val saved = runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(csv.toByteArray(Charsets.UTF_8))
                            } ?: error("无法写入所选位置")
                        }
                    }
                    Toast.makeText(
                        context,
                        if (saved.isSuccess) "工时明细已保存到所选位置" else "保存失败，请重试",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    // 导入：先读取摘要，弹确认框后才真正覆盖
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) backupViewModel.prepareRestore(context, uri) }

    LaunchedEffect(backupMessage) {
        backupMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            backupViewModel.clearMessage()
        }
    }

    if (pending != null) {
        val summary = pending!!
        val exportDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date(summary.exportTime))
        AlertDialog(
            onDismissRequest = { backupViewModel.cancelRestore() },
            title = { Text("确认导入恢复") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("备份导出时间：$exportDate")
                    Text("打卡记录：${summary.eventCount} 条")
                    Text("工时段：${summary.sessionCount} 条")
                    Text(
                        "导入将覆盖当前全部数据，操作不可撤销。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { backupViewModel.confirmRestore() }) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = { backupViewModel.cancelRestore() }) { Text("取消") }
            }
        )
    }

    SettingsPage(
        title = "数据管理",
        subtitle = "数据仅存于本机，请自行备份",
        onBack = onBack
    ) {
        SettingsGroupCard {
            Text(
                "数据仅存于本机。换机前导出 JSON 备份；查看工时用 CSV 明细。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 导出 JSON 备份：选位置保存，用于换机恢复
            Button(
                onClick = { jsonExportLauncher.launch("工时备份_${System.currentTimeMillis()}.json") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) { Text("导出备份（换机恢复）") }
            // 导出 CSV 明细：表格软件可打开观看
            OutlinedButton(
                onClick = { csvExportLauncher.launch("工时明细_${System.currentTimeMillis()}.csv") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) { Text("导出工时明细（CSV，可查看）") }
        }

        SettingsGroupCard {
            Text(
                "导入会覆盖当前全部数据，选中文件后还会再次确认。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            // 导入恢复：危险操作，用 error 色与导出区分；选文件后仍需确认摘要
            OutlinedButton(
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("导入备份恢复（覆盖当前数据）") }
        }
    }
}
