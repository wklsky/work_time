/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/today/DayTape.kt
 * @Description: 今日页签名元素「时间带」：24 小时刻度上绘制计划班次、已记录工时段与当前时刻指针
 */
package com.worktime.pro.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.domain.model.SessionType
import java.util.Locale
import kotlin.math.roundToInt

/** 时间带上的段类型：正常工时用品牌蓝，加班用加班橙 */
internal enum class TapeBandKind { WORK, OVERTIME }

/**
 * 时间带上的一段。
 * open=true 表示该段尚未闭合，末端延伸到「现在」，绘制时降透明度以区分已结算工时。
 */
internal data class TapeBand(
    val startMinute: Float,
    val endMinute: Float,
    val kind: TapeBandKind,
    val open: Boolean
)

/**
 * 由已结算工时段 + 未闭合的上下班段拼出今日时间带。
 * 所有时间按「当天 00:00 起的分钟数」表示，跨天段自动裁到 0 点或 24 点。
 */
internal fun buildTapeBands(
    sessions: List<WorkSessionEntity>,
    openWorkStartMillis: Long?,
    openOtStartMillis: Long?,
    nowMillis: Long,
    dayStartMillis: Long
): List<TapeBand> {
    val bands = mutableListOf<TapeBand>()

    fun minuteOf(epochMillis: Long): Float =
        ((epochMillis - dayStartMillis) / 60_000f).coerceIn(0f, 1440f)

    sessions.forEach { session ->
        val start = minuteOf(session.startTime)
        val end = minuteOf(session.endTime)
        if (end - start >= 1f) {
            bands += TapeBand(
                startMinute = start,
                endMinute = end,
                kind = if (session.sessionType == SessionType.OVERTIME.name) {
                    TapeBandKind.OVERTIME
                } else {
                    TapeBandKind.WORK
                },
                open = false
            )
        }
    }

    fun addOpenSegment(startMillis: Long?, kind: TapeBandKind) {
        if (startMillis == null) return
        val start = minuteOf(startMillis)
        val end = minuteOf(nowMillis)
        if (end - start >= 1f) {
            bands += TapeBand(start, end, kind, open = true)
        }
    }
    addOpenSegment(openWorkStartMillis, TapeBandKind.WORK)
    addOpenSegment(openOtStartMillis, TapeBandKind.OVERTIME)

    return bands.sortedBy { it.startMinute }
}

/**
 * 24 小时时间带。横向刻度固定 00–24，是共享的时间坐标而非打卡制式，
 * 因此轴标签不随 12/24 小时设置变化；下方打卡时刻仍按用户设置展示。
 */
@Composable
internal fun DayTape(
    bands: List<TapeBand>,
    nowMinuteOfDay: Float,
    plannedStartMinute: Float,
    plannedEndMinute: Float,
    modifier: Modifier = Modifier
) {
    val ruleColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
    val plannedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val needleColor = MaterialTheme.colorScheme.onSurface
    val workColor = MaterialTheme.colorScheme.secondary
    val overtimeColor = MaterialTheme.colorScheme.tertiary

    val description = remember(bands) {
        if (bands.isEmpty()) {
            "今日时间带，还没有打卡记录"
        } else {
            "今日时间带：" + bands.joinToString("；") { band ->
                val kind = if (band.kind == TapeBandKind.WORK) "正常工作" else "加班"
                val end = if (band.open) "至今" else minuteLabel(band.endMinute)
                "$kind ${minuteLabel(band.startMinute)} 至 $end"
            }
        }
    }

    Column(modifier = modifier.semantics(mergeDescendants = true) {
        contentDescription = description
    }) {
        Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
            val width = size.width
            val trackHeight = 16.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = top + trackHeight

            fun xOf(minute: Float): Float =
                (minute.coerceIn(0f, 1440f) / 1440f) * width

            // 轨道底：浅色时间带，让左右边界一眼可见
            drawRoundRect(
                color = trackColor.copy(alpha = 0.55f),
                topLeft = Offset(0f, top),
                size = Size(width, trackHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )

            // 计划班次：虚线框，代表「应该在公司」的时段
            if (plannedEndMinute > plannedStartMinute) {
                val startX = xOf(plannedStartMinute)
                val endX = xOf(plannedEndMinute)
                drawRoundRect(
                    color = plannedColor.copy(alpha = 0.85f),
                    topLeft = Offset(startX, top),
                    size = Size(endX - startX, trackHeight),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                        )
                    )
                )
            }

            // 已记录工时段：实心墨条；未闭合段降透明度
            bands.forEach { band ->
                val startX = xOf(band.startMinute)
                val endX = xOf(band.endMinute)
                val bandWidth = (endX - startX).coerceAtLeast(3.dp.toPx())
                val bandColor = if (band.kind == TapeBandKind.WORK) workColor else overtimeColor
                drawRoundRect(
                    color = bandColor.copy(alpha = if (band.open) 0.55f else 1f),
                    topLeft = Offset(startX, top + 1.dp.toPx()),
                    size = Size(bandWidth, trackHeight - 2.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }

            // 时刻刻度：每 3 小时一短刻，每 6 小时一长刻
            for (hour in 0..24 step 3) {
                val tickX = xOf(hour * 60f).coerceIn(0f, width - 1f)
                val tickHeight = if (hour % 6 == 0) 4.dp.toPx() else 2.dp.toPx()
                drawLine(
                    color = ruleColor,
                    start = Offset(tickX, bottom),
                    end = Offset(tickX, bottom + tickHeight),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 现在：一根墨针穿过轨道，顶部圆点标出当前位置
            val nowX = xOf(nowMinuteOfDay).coerceIn(1.dp.toPx(), width - 1.dp.toPx())
            drawLine(
                color = needleColor,
                start = Offset(nowX, top - 3.dp.toPx()),
                end = Offset(nowX, bottom + 3.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = needleColor,
                radius = 2.5.dp.toPx(),
                center = Offset(nowX, top - 4.dp.toPx())
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AxisLabel("00")
            AxisLabel("12")
            AxisLabel("24")
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun minuteLabel(minute: Float): String {
    val total = minute.roundToInt().coerceIn(0, 1440)
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}
