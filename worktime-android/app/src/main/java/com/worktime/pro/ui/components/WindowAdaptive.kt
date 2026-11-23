/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/components/WindowAdaptive.kt
 * @Description: 窗口自适应工具，按窗口宽度划分尺寸档位并提供内容限宽容器，保证小屏不挤压、大屏不拉宽
 */
package com.worktime.pro.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 窗口宽度档位，断点对齐 Material3 规范，但不额外引入 window-size-class 依赖：
 * COMPACT 竖屏手机、MEDIUM 大屏手机/折叠展开/手机横屏、EXPANDED 平板。
 */
enum class AppWindowSize { COMPACT, MEDIUM, EXPANDED }

/**
 * 单列内容最大宽度。
 * 平板与横屏下若让单列内容铺满整屏，正文行宽超过约 90 个字符就会明显增加阅读负担，
 * 因此统一限宽 640dp 居中，留白换取可读性。
 */
val CONTENT_MAX_WIDTH: Dp = 640.dp

/** 按当前窗口宽度取档位；用真实窗口宽而非整屏宽，分屏/自由窗口/横屏下断点才正确 */
@Composable
@ReadOnlyComposable
fun rememberAppWindowSize(): AppWindowSize {
    val width = currentWindowWidthDp()
    return when {
        width >= 840 -> AppWindowSize.EXPANDED
        width >= 600 -> AppWindowSize.MEDIUM
        else -> AppWindowSize.COMPACT
    }
}

/**
 * 当前应用窗口宽度（dp）。
 * 手机分屏时 screenWidthDp 仍是整屏宽，会把半个屏误判成大屏；
 * 改用 LocalView 的实测宽度（取自 Activity 内容视图的布局尺寸，分屏/自由窗口下即真实可用宽），
 * 取不到（为 0，首帧布局前）时回落到 screenWidthDp 兜底。
 */
@Composable
@ReadOnlyComposable
private fun currentWindowWidthDp(): Int {
    val windowWidthPx = LocalView.current.width.toFloat()
    if (windowWidthPx > 0f) {
        val density = LocalDensity.current
        return with(density) { windowWidthPx.toDp().value.roundToInt() }
    }
    return LocalConfiguration.current.screenWidthDp
}

/**
 * 内容限宽容器：子页面统一套在导航骨架内层，宽屏居中不出格，窄屏自然全宽。
 * 修饰符顺序必须是 widthIn 在前、fillMaxSize 在后，否则 fillMaxSize 会先吃掉父级最大宽度导致限宽失效。
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = CONTENT_MAX_WIDTH,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier.widthIn(max = maxWidth).fillMaxSize(),
            content = content
        )
    }
}
