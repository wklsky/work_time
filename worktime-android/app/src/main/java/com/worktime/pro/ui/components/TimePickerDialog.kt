/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/components/TimePickerDialog.kt
 * @Description: 通用时间选择弹窗「校时」：24 小时卡尺时间带 + 大号等宽读数 + 快捷微调，
 *               替代 Material3 表盘样式，统一今日页/手动录入/习惯打卡/工作规则的选时体验。
 */
package com.worktime.pro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

private const val MINUTES_IN_DAY = 24 * 60

/** 卡尺上每分钟占用的宽度：1 小时约 180dp，一屏能看到约两小时，分钟刻度仍可逐格吸附 */
private val MINUTE_WIDTH = 3.dp
private val RULER_HEIGHT = 96.dp
private val RULER_LABEL_WIDTH = 36.dp

/**
 * 时间选择弹窗。
 *
 * @param title 业务标题，如「打卡时刻」「上班时间」「日切时间」
 * @param subtitle 读数下方的上下文说明；为空时使用操作提示
 */
@Composable
fun TimePickerDialog(
    title: String,
    initialTime: LocalTime,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    subtitle: String? = null
) {
    val density = LocalDensity.current
    val minuteWidthPx = with(density) { MINUTE_WIDTH.toPx() }
    val initialMinute = initialTime.hour * 60 + initialTime.minute
    val scrollState = rememberScrollState(initial = (initialMinute * minuteWidthPx).roundToInt())
    val scope = rememberCoroutineScope()

    // 读数跟随时间带实时变化，滚动过程中也能看到当前落点
    val selectedMinute by remember(minuteWidthPx) {
        derivedStateOf {
            (scrollState.value / minuteWidthPx).roundToInt().coerceIn(0, MINUTES_IN_DAY - 1)
        }
    }
    val nowMinute = remember { LocalTime.now().let { it.hour * 60 + it.minute } }
    val selectedTime = LocalTime.of(selectedMinute / 60, selectedMinute % 60)

    // 松手后吸附到最近的分钟刻度，像卡尺一样「咬合」
    LaunchedEffect(minuteWidthPx) {
        snapshotFlow { scrollState.isScrollInProgress }
            .filter { inProgress -> !inProgress && scrollState.maxValue != Int.MAX_VALUE }
            .collect {
                val target = ((scrollState.value / minuteWidthPx).roundToInt() * minuteWidthPx)
                    .roundToInt()
                    .coerceIn(0, scrollState.maxValue)
                if (scrollState.value != target) scrollState.animateScrollTo(target)
            }
    }

    fun jumpTo(minute: Int) {
        val target = (minute.coerceIn(0, MINUTES_IN_DAY - 1) * minuteWidthPx).roundToInt()
        scope.launch { scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue)) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "校时",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 3.sp
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            TimeFormatter.formatTime(selectedTime, use24Hour),
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-1).sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle ?: "拖动时间带校准，或点按快捷调整",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    TimeTape(
                        scrollState = scrollState,
                        minuteWidth = MINUTE_WIDTH,
                        nowMinute = nowMinute,
                        selectedTimeLabel = TimeFormatter.formatTime(selectedTime, use24Hour)
                    )

                    Spacer(Modifier.height(16.dp))

                    // 大跨度用小时键、落点用 5 分钟键，避免在 24 小时带上反复甩动
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickKey("现在", Modifier.weight(1f)) { jumpTo(nowMinute) }
                        QuickKey("−1时", Modifier.weight(1f)) { jumpTo(selectedMinute - 60) }
                        QuickKey("−5", Modifier.weight(1f)) { jumpTo(selectedMinute - 5) }
                        QuickKey("+5", Modifier.weight(1f)) { jumpTo(selectedMinute + 5) }
                        QuickKey("+1时", Modifier.weight(1f)) { jumpTo(selectedMinute + 60) }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { onConfirm(selectedTime) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "使用 ${TimeFormatter.formatTime(selectedTime, use24Hour)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 签名元素：24 小时卡尺时间带。
 * 中心固定一根指针，刻度随拖动平移；整点刻度最高并带小时标签，每 5 / 15 分钟逐级加长。
 * 当前位置用一根淡虚线与小圆点标出，方便判断离现在多远。
 */
@Composable
private fun TimeTape(
    scrollState: androidx.compose.foundation.ScrollState,
    minuteWidth: Dp,
    nowMinute: Int,
    selectedTimeLabel: String
) {
    val density = LocalDensity.current
    val minuteWidthPx = with(density) { minuteWidth.toPx() }
    val ruleColor = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelWidthPx = with(density) { RULER_LABEL_WIDTH.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .clearAndSetSemantics {
                contentDescription = "24 小时时间带，当前选择 $selectedTimeLabel，拖动可校准"
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val centerX = widthPx / 2f
        // 内层 Box 的 BoxScope 会遮蔽 BoxWithConstraints 的 maxWidth，先在此处取好内容宽度
        val contentWidth = maxWidth + minuteWidth * (MINUTES_IN_DAY - 1).toFloat()

        // 仅用于提供滚动范围：内容宽度 = 屏幕宽度 + 一天剩余分钟的刻度宽度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            Spacer(
                modifier = Modifier
                    .width(contentWidth)
                    .height(1.dp)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val scroll = scrollState.value.toFloat()
            val baseline = size.height - 6.dp.toPx()
            val labelBottom = 20.dp.toPx()

            // 基线：让刻度有落脚的「尺面」
            drawLine(
                color = ruleColor.copy(alpha = 0.35f),
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx()
            )

            val firstMinute = ((scroll - centerX) / minuteWidthPx).toInt() - 2
            val lastMinute = ((scroll + centerX) / minuteWidthPx).toInt() + 2
            for (minute in firstMinute..lastMinute) {
                if (minute < 0 || minute >= MINUTES_IN_DAY) continue
                val x = centerX + minute * minuteWidthPx - scroll
                val minuteOfHour = minute % 60
                val (tickHeight, alpha, stroke) = when {
                    minuteOfHour == 0 -> Triple(22.dp, 0.9f, 1.5.dp)
                    minuteOfHour % 15 == 0 -> Triple(13.dp, 0.65f, 1.dp)
                    minuteOfHour % 5 == 0 -> Triple(8.dp, 0.45f, 1.dp)
                    else -> Triple(4.dp, 0.28f, 1.dp)
                }
                drawLine(
                    color = ruleColor.copy(alpha = alpha),
                    start = Offset(x, baseline),
                    end = Offset(x, baseline - tickHeight.toPx()),
                    strokeWidth = stroke.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 现在：淡虚线 + 小圆点，作为选时参照
            val nowX = centerX + nowMinute * minuteWidthPx - scroll
            if (nowX in 4.dp.toPx()..(size.width - 4.dp.toPx())) {
                drawLine(
                    color = muted.copy(alpha = 0.5f),
                    start = Offset(nowX, labelBottom),
                    end = Offset(nowX, baseline),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(3.dp.toPx(), 3.dp.toPx())
                    )
                )
                drawCircle(
                    color = muted.copy(alpha = 0.6f),
                    radius = 2.dp.toPx(),
                    center = Offset(nowX, labelBottom)
                )
            }

            // 选中光带 + 指针：整个时间带唯一的彩色元素
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        needleColor.copy(alpha = 0f),
                        needleColor.copy(alpha = 0.10f),
                        needleColor.copy(alpha = 0f)
                    ),
                    startX = centerX - 26.dp.toPx(),
                    endX = centerX + 26.dp.toPx()
                ),
                topLeft = Offset(centerX - 26.dp.toPx(), 0f),
                size = Size(52.dp.toPx(), size.height)
            )
            drawLine(
                color = needleColor,
                start = Offset(centerX, labelBottom),
                end = Offset(centerX, baseline + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = needleColor,
                radius = 3.dp.toPx(),
                center = Offset(centerX, labelBottom)
            )

            // 两端渐隐，暗示刻度可以继续滑动
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(surfaceColor, surfaceColor.copy(alpha = 0f))
                ),
                size = Size(28.dp.toPx(), size.height)
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(surfaceColor.copy(alpha = 0f), surfaceColor)
                ),
                topLeft = Offset(size.width - 28.dp.toPx(), 0f),
                size = Size(28.dp.toPx(), size.height)
            )
        }

        // 小时标签作为文本叠在刻度上方，保留字体排版而不在 Canvas 里画字。
        // 滚动读数放在 offset 的布局 lambda 里，避免拖动时整块标签参与重组
        for (hour in 0..24) {
            Box(
                modifier = Modifier
                    .offset {
                        val xCenter = centerX + hour * 60f * minuteWidthPx - scrollState.value
                        IntOffset(
                            (xCenter - labelWidthPx / 2f).roundToInt(),
                            2.dp.roundToPx()
                        )
                    }
                    .width(RULER_LABEL_WIDTH)
            ) {
                Text(
                    text = if (hour == 24) "24" else String.format(Locale.US, "%02d", hour),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = muted.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
