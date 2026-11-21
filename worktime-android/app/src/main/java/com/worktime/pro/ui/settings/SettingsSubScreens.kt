/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsSubScreens.kt
 * @Description: 设置二级页面，按分组拆分为打卡、显示、提醒、状态映射与数据管理五个独立页面
 */
package com.worktime.pro.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.ThemeMode
import com.worktime.pro.data.datastore.TimeFormatMode
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.reminder.ReminderType
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.theme.CustomPalette
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 打卡与识别：默认动作、OCR 填充、照片保存，以及工作规则与习惯打卡两个三级入口 */
@Composable
fun SettingsPunchScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenHabits: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val context = LocalContext.current
    val currentDefaultPunch = PunchType.entries.firstOrNull { it.name == settings.defaultPunchType }
        ?: PunchType.WORK_START

    // Android 8–9 写公共相册需要存储权限；拒绝时仅保留应用内凭证
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setGallerySaveEnabled(true)
        } else {
            Toast.makeText(context, "未授予存储权限，照片只会保存在应用内", Toast.LENGTH_LONG).show()
        }
    }

    SettingsPage(
        title = "打卡与识别",
        subtitle = "默认动作、识别填充与照片保存",
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
            SwitchRow(
                title = "OCR 自动填充",
                description = "开启后识别结果自动填入确认页，仍需人工确认才入库",
                checked = settings.ocrAutoFill,
                onCheckedChange = viewModel::setOcrAutoFill
            )
            ItemDivider()
            SwitchRow(
                title = "保存到系统相册",
                description = "确认保存后写入 Pictures/工时助手；备份不含照片",
                checked = settings.gallerySaveEnabled,
                onCheckedChange = { enabled ->
                    val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    if (enabled && needsPermission) {
                        galleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        viewModel.setGallerySaveEnabled(enabled)
                    }
                }
            )
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

    // Android 13+ 发送提醒需要通知权限
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setReminderEnabled(true)
        } else {
            Toast.makeText(context, "未授予通知权限，提醒无法发送", Toast.LENGTH_LONG).show()
        }
    }

    SettingsPage(
        title = "提醒",
        subtitle = "累计工时达标后发送系统通知",
        onBack = onBack
    ) {
        SettingsGroupCard {
            SwitchRow(
                title = "累计提醒",
                description = "达到设定时长后发送系统通知，后台可能略有延迟",
                checked = settings.reminderEnabled,
                onCheckedChange = { enabled ->
                    val needsPermission = enabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setReminderEnabled(enabled)
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
                }
            }
        }
    }
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

/** 考勤机状态映射：独立成页后直接铺开 0-9 状态位，不再折叠，省掉一次点击 */
@Composable
fun SettingsMappingScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())

    SettingsPage(
        title = "考勤机状态映射",
        subtitle = "状态列数字决定识别出的打卡类型",
        onBack = onBack
    ) {
        SettingsGroupCard {
            Text(
                "识别考勤机截图时，状态列的数字按这里的映射判定打卡类型；留空表示不识别该状态。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusMappingEditor(
                raw = settings.statusMapping,
                onChanged = { viewModel.setStatusMapping(it) },
                onReset = { viewModel.resetStatusMapping() }
            )
        }
    }
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
