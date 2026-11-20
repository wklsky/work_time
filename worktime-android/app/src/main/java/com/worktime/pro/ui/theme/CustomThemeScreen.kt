/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/theme/CustomThemeScreen.kt
 * @Description: 自定义配色与字号界面，提供预设、取色、实时预览和对比度提示
 */
package com.worktime.pro.ui.theme

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.ThemeMode
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.ColorPickerDialog
import com.worktime.pro.util.ColorContrast
import com.worktime.pro.util.ContrastLevel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomThemeScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: CustomThemeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val appSettings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val systemDark = isSystemInDarkTheme()
    val isBlackWhite = appSettings.themeMode == ThemeMode.BLACK_WHITE.value
    val appUsesDark = appSettings.themeMode == ThemeMode.DARK.value ||
        isBlackWhite ||
        (appSettings.themeMode == ThemeMode.SYSTEM.value && systemDark)
    val appModeLabel = when {
        isBlackWhite -> "黑白"
        appUsesDark -> "深色"
        else -> "浅色"
    }
    var pickingSlot by remember { mutableStateOf<PaletteSlot?>(null) }

    // 默认编辑当前生效的浅色/深色配色，避免用户改了另一套却看不到变化
    LaunchedEffect(appUsesDark) {
        viewModel.setEditDark(appUsesDark)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("自定义配色", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "浅色/深色分别保存，实时预览",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onBack) { Text("完成") }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前应用：$appModeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (state.enabled) "自定义已启用" else "自定义未启用",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.enabled) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (isBlackWhite) {
            Text(
                "当前为黑白主题，自定义配色不会生效；请先在设置 → 主题切换到浅色或深色。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            TextButton(onClick = viewModel::applyEditingModeToApp) {
                Text("切换到${if (state.editDark) "深色" else "浅色"}主题")
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用自定义配色", fontWeight = FontWeight.Medium)
                        Text(
                            "修改颜色会自动启用；黑白模式保持独立",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = viewModel::setEnabled
                    )
                }

                if (!state.enabled) {
                    Text(
                        "修改任意颜色或应用预设会自动启用，也可以手动打开上方开关",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.editDark,
                        onClick = { viewModel.setEditDark(false) },
                        label = { Text("浅色") }
                    )
                    FilterChip(
                        selected = state.editDark,
                        onClick = { viewModel.setEditDark(true) },
                        label = { Text("深色") }
                    )
                }

                val editingMatchesApp = state.editDark == appUsesDark
                Text(
                    if (editingMatchesApp) {
                        "正在编辑当前生效的${if (state.editDark) "深色" else "浅色"}配色"
                    } else {
                        "正在编辑${if (state.editDark) "深色" else "浅色"}配色；应用当前为$appModeLabel，切换到对应主题后才可见"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (editingMatchesApp) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
                if (!editingMatchesApp) {
                    TextButton(onClick = viewModel::applyEditingModeToApp) {
                        Text("切换到${if (state.editDark) "深色" else "浅色"}主题")
                    }
                }

                Text("预设", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CustomPalettePresets.ALL.forEach { preset ->
                        FilterChip(
                            selected = state.palette == preset.light || state.palette == preset.dark,
                            onClick = { viewModel.applyPreset(preset) },
                            label = { Text(preset.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(preset.light.primary)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("颜色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PaletteSlot.entries.forEach { slot ->
                    val color = viewModel.colorOf(slot)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { pickingSlot = slot }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp)
                                )
                        )
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        ) {
                            Text(slot.label)
                            Text(
                                slot.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                color.toArgb().toHex(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "修改",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("实时预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = state.palette.background)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(state.palette.surface)
                    .padding(16.dp)
            ) {
                Text(
                    "2026年11月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = state.palette.textPrimary
                )
                Text(
                    "出勤 12 天 · 正常 92.5h",
                    style = MaterialTheme.typography.bodySmall,
                    color = state.palette.textSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewPill(state.palette.work, "正常 9.5h")
                    PreviewPill(state.palette.overtime, "加班 0.5h")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("字号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontScaleChip(0.9f, "小", state.fontScale, viewModel::setFontScale)
                    FontScaleChip(1.0f, "标准", state.fontScale, viewModel::setFontScale)
                    FontScaleChip(1.15f, "大", state.fontScale, viewModel::setFontScale)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val palette = state.palette
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "可读性检查",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ContrastRow(
                    "正文 / 页面背景",
                    ColorContrast.ratio(palette.textPrimary, palette.background)
                )
                ContrastRow(
                    "次要文字 / 页面背景",
                    ColorContrast.ratio(palette.textSecondary, palette.background)
                )
                ContrastRow(
                    "正文 / 卡片表面",
                    ColorContrast.ratio(palette.textPrimary, palette.surface)
                )
                Text(
                    "建议 ≥ 4.5:1；偏低时可用下方按钮快速修正",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = viewModel::autoAdaptTextColors) {
                        Text("自动适配文字颜色")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { viewModel.resetPalette() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复当前模式默认配色")
        }
    }

    pickingSlot?.let { slot ->
        val palette = state.palette
        val contrastAgainst = when (slot) {
            PaletteSlot.TEXT_PRIMARY, PaletteSlot.TEXT_SECONDARY -> palette.background
            PaletteSlot.BACKGROUND, PaletteSlot.SURFACE -> palette.textPrimary
            PaletteSlot.PRIMARY, PaletteSlot.WORK, PaletteSlot.OVERTIME -> palette.background
        }
        val contrastLabel = when (slot) {
            PaletteSlot.BACKGROUND -> "正文与背景对比度"
            PaletteSlot.SURFACE -> "正文与卡片对比度"
            else -> "与背景对比度"
        }
        val swatches = listOf(
            palette.primary,
            palette.work,
            palette.overtime,
            palette.background,
            palette.surface,
            palette.textPrimary,
            palette.textSecondary,
            Color.White,
            Color.Black
        ).distinct()

        ColorPickerDialog(
            title = slot.label,
            initialColor = viewModel.colorOf(slot),
            defaultColor = viewModel.defaultColorOf(slot),
            contrastAgainst = contrastAgainst,
            contrastLabel = contrastLabel,
            swatches = swatches,
            onDismiss = { pickingSlot = null },
            onConfirm = { color ->
                viewModel.updateColor(slot, color)
                pickingSlot = null
            }
        )
    }
}

@Composable
private fun PreviewPill(color: Color, label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CustomPalette.contrastOn(color)
        )
    }
}

@Composable
private fun ContrastRow(label: String, ratio: Float) {
    val level = ColorContrast.level(ratio)
    val (text, color) = when (level) {
        ContrastLevel.GOOD -> "良好" to MaterialTheme.colorScheme.secondary
        ContrastLevel.WARN -> "偏低" to MaterialTheme.colorScheme.tertiary
        ContrastLevel.POOR -> "不足" to MaterialTheme.colorScheme.error
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "${"%.1f".format(Locale.US, ratio)}:1",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "  $text",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun FontScaleChip(
    value: Float,
    label: String,
    current: Float,
    onSelect: (Float) -> Unit
) {
    FilterChip(
        selected = kotlin.math.abs(current - value) < 0.01f,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

private fun Int.toHex(): String = String.format(Locale.US, "#%06X", this and 0xFFFFFF)
