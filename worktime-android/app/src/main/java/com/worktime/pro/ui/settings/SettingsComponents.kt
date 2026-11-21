/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsComponents.kt
 * @Description: 设置模块共享组件，提供页头、纸面卡片、目录行与各类设置行，供目录页与子页复用
 */
package com.worktime.pro.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.ocr.StatusMapping

/**
 * 设置页通用骨架：固定页头 + 可滚动内容。
 * 页头固定是为了任何时候都知道自己在哪一级、都能直接返回，避免子页滚动后失去导航线索。
 */
@Composable
internal fun SettingsPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** 分区小标题：目录页用它把入口分成「使用偏好」与「数据与高级」，避免五个入口平铺成一锅 */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 纸面卡片容器：与今日页同一套 surface + 描边语言，子页内按语义分组 */
@Composable
internal fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp), content = content)
    }
}

/**
 * 目录行：图标 + 标题 + 当前值摘要 + 箭头。
 * 摘要放在标题下方而不是右侧：设置值往往是「浅色 · 24 小时」这类组合，右侧会挤掉标题。
 */
@Composable
internal fun SettingsEntryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 卡内细分割线：分开相邻选项，但不割裂成多张卡 */
@Composable
internal fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** 选项块：标题在上、选项在下，chips 自动换行，窄屏不会横向溢出 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipBlock(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 通用行：左标题+说明，右侧控件；onClick 让整行可点，扩大命中区域 */
@Composable
internal fun SettingRow(
    title: String,
    description: String? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = descriptionColor)
            }
        }
        trailing()
    }
}

/** 跳转行：整行可点 + 右侧箭头，比单独的「管理」文字按钮更好按，也更像系统设置 */
@Composable
internal fun LinkRow(title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 开关行：整行可点，点文字也能切换，比只点开关好按 */
@Composable
internal fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingRow(
        title = title,
        description = description,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

/** 小圆点：自定义配色的三色预览，让人不点进去也能看出当前配色 */
@Composable
internal fun ColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

/**
 * 状态映射编辑器：考勤机状态列数字（0-9）→ 打卡类型。
 * raw 为空时展示内置默认映射，用户改动后才写入自定义值，避免默认值被固化进偏好。
 */
@Composable
internal fun StatusMappingEditor(
    raw: String,
    onChanged: (String) -> Unit,
    onReset: () -> Unit
) {
    val mapping = if (raw.isBlank()) StatusMapping.DEFAULT else StatusMapping.deserialize(raw)
    // 只展示 0-9 状态位，覆盖常见考勤机编码；每行用下拉选择，避免小屏溢出
    val statusCodes = (0..9).map { it.toString() }

    statusCodes.forEach { code ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
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

    TextButton(onClick = onReset, modifier = Modifier.padding(horizontal = 16.dp)) {
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

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: "无",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // 不用固定宽度：窄屏（320dp）与大字号下固定 128dp 会把左侧「状态 N」挤掉
            modifier = Modifier.menuAnchor().widthIn(min = 96.dp, max = 168.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
