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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** 按当前窗口宽度取档位；用 configuration 而不是每页套一层 BoxWithConstraints，避免重复测量 */
@Composable
@ReadOnlyComposable
fun rememberAppWindowSize(): AppWindowSize {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width >= 840 -> AppWindowSize.EXPANDED
        width >= 600 -> AppWindowSize.MEDIUM
        else -> AppWindowSize.COMPACT
    }
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
