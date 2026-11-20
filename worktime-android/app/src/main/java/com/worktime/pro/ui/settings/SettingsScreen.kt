/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsScreen.kt
 * @Description: 设置界面，含全局日历显示切换、打卡设置与本地数据备份恢复
 */
package com.worktime.pro.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.ThemeMode
import com.worktime.pro.data.datastore.TimeFormatMode
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.ocr.StatusMapping
import com.worktime.pro.domain.reminder.ReminderType
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.theme.CustomPalette
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenCustomTheme: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val backupViewModel: BackupViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val backupMessage by backupViewModel.message.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentMode = CalendarDisplayMode.entries.firstOrNull { it.value == settings.calendarDisplayMode }
        ?: CalendarDisplayMode.WORK_TIME

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

    val pending by backupViewModel.pending.collectAsState()

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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(icon = Icons.Filled.Palette, title = "显示设置")

                Text("主题", style = MaterialTheme.typography.bodyMedium)
                val currentTheme = ThemeMode.entries.firstOrNull { it.value == settings.themeMode }
                    ?: ThemeMode.SYSTEM
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentTheme == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自定义配色", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                currentTheme == ThemeMode.BLACK_WHITE && settings.customThemeEnabled ->
                                    "已启用，但黑白模式不使用自定义配色"
                                settings.customThemeEnabled ->
                                    "已启用 · 品牌色、工时色、加班色与文字颜色"
                                else ->
                                    "未启用 · 修改颜色会自动启用"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                currentTheme == ThemeMode.BLACK_WHITE && settings.customThemeEnabled ->
                                    MaterialTheme.colorScheme.tertiary
                                settings.customThemeEnabled -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    val editingDark = settings.themeMode == ThemeMode.DARK.value ||
                        (settings.themeMode == ThemeMode.SYSTEM.value && isSystemInDarkTheme())
                    val previewPalette = CustomPalette.deserialize(
                        if (editingDark) settings.customDarkColors else settings.customLightColors
                    ) ?: if (editingDark) {
                        CustomPalette.defaultDark()
                    } else {
                        CustomPalette.defaultLight()
                    }
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            previewPalette.primary,
                            previewPalette.work,
                            previewPalette.overtime
                        ).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                            )
                        }
                    }
                    TextButton(onClick = onOpenCustomTheme) { Text("管理") }
                }

                Text("时间制式", style = MaterialTheme.typography.bodyMedium)
                val currentTimeFormat = TimeFormatMode.entries.firstOrNull { it.value == settings.timeFormat }
                    ?: TimeFormatMode.SYSTEM
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Text("日历每日显示内容", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalendarDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentMode == mode,
                            onClick = { viewModel.setCalendarDisplayMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text(
                    "全局统一生效：可选择在日历中显示全部、仅上班时长或仅加班时长。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SwitchRow(
                    title = "加班色阶",
                    description = "日历中按加班时长显示底色深浅",
                    checked = settings.showOvertimeShade,
                    onCheckedChange = viewModel::setOvertimeShade
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(icon = Icons.Filled.TouchApp, title = "打卡设置")
                SwitchRow(
                    title = "OCR 自动填充",
                    description = "开启后识别结果自动填入确认页，仍需人工确认才入库",
                    checked = settings.ocrAutoFill,
                    onCheckedChange = viewModel::setOcrAutoFill
                )
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
                // 工作规则入口：班次、午休、缓冲、日切与工作日
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("工作规则", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "上下班、午休、加班缓冲、日切与工作日",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenRules) { Text("管理") }
                }
                // 习惯打卡管理入口：命名固定班次时间，首页一键应用
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("习惯打卡", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "管理固定班次时间，首页可一键应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenHabits) { Text("管理") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionTitle(icon = Icons.Filled.Notifications, title = "提醒")
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
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else {
                            viewModel.setReminderEnabled(enabled)
                        }
                    }
                )
                if (settings.reminderEnabled) {
                    Text("提醒类型", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderType.entries.forEach { type ->
                            FilterChip(
                                selected = settings.reminderType == type.value,
                                onClick = { viewModel.setReminderType(type) },
                                label = { Text(type.label) }
                            )
                        }
                    }

                    var reminderValue by remember(settings.reminderMinutes) {
                        mutableFloatStateOf(settings.reminderMinutes.toFloat())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("提醒阈值", modifier = Modifier.weight(1f))
                        Text(
                            TimeFormatter.formatDuration(
                                (reminderValue / 30f).roundToInt() * 30
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = reminderValue,
                        onValueChange = { reminderValue = it },
                        onValueChangeFinished = {
                            viewModel.setReminderMinutes(
                                (reminderValue / 30f).roundToInt() * 30
                            )
                        },
                        valueRange = 60f..720f,
                        steps = 21
                    )
                    Text(
                        "例如加班满 7 小时提醒；达到阈值提醒一次，修改类型或阈值后可再次提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 状态映射：考勤机状态列数字 → 打卡类型，用户可按机器编码自定义
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(icon = Icons.Filled.Numbers, title = "考勤机状态映射")
                Text(
                    "设置考勤机状态列数字对应的打卡类型，识别时按此映射判定。",
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

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(icon = Icons.Filled.Storage, title = "数据管理")
                Text(
                    "数据仅存于本机。换机前导出 JSON 备份；查看工时用 CSV 明细。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 导出 JSON 备份：选位置保存，用于换机恢复
                Button(
                    onClick = { jsonExportLauncher.launch("工时备份_${System.currentTimeMillis()}.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导出备份（换机恢复）") }
                // 导出 CSV 明细：表格软件可打开观看
                OutlinedButton(
                    onClick = { csvExportLauncher.launch("工时明细_${System.currentTimeMillis()}.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导出工时明细（CSV，可查看）") }
                // 导入恢复：选文件后先弹摘要确认
                OutlinedButton(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导入备份恢复") }
                Text(
                    "注意：导入会覆盖当前全部数据，确认后再操作，不可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 分组标题：图标色块 + 标题，让四个设置分区一眼可辨 */
@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.secondary)
        }
        Text(title, modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 状态映射编辑器：考勤机状态列数字（0-9）→ 打卡类型。
 * raw 为空时展示内置默认映射，用户改动后才写入自定义值。
 */
@Composable
private fun StatusMappingEditor(
    raw: String,
    onChanged: (String) -> Unit,
    onReset: () -> Unit
) {
    val mapping = if (raw.isBlank()) StatusMapping.DEFAULT else StatusMapping.deserialize(raw)
    // 只展示 0-9 状态位，覆盖常见考勤机编码；每行用下拉选择，避免小屏溢出
    val statusCodes = (0..9).map { it.toString() }

    statusCodes.forEach { code ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("状态 $code", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            StatusTypeDropdown(
                selected = mapping[code],
                onSelected = { type ->
                    val updated = mapping.toMutableMap()
                    if (type == null) updated.remove(code) else updated[code] = type
                    onChanged(StatusMapping.serialize(updated))
                }
            )
        }
    }

    TextButton(onClick = onReset) {
        Text("恢复默认映射（0上班 1下班 4加班到 5加班退）")
    }
}

/** 状态位 → 打卡类型下拉选择 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusTypeDropdown(
    selected: PunchType?,
    onSelected: (PunchType?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options: List<PunchType?> = buildList {
        add(null)
        addAll(PunchType.entries)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.label ?: "无",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().width(128.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type?.label ?: "无") },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}
