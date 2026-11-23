/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/MainActivity.kt
 * @Description: 应用主 Activity，三 Tab 底部导航（首页/今日/我的）与子页面路由，含全局依赖注入
 */
package com.worktime.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.worktime.pro.ui.LocalRepository
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.AdaptiveContent
import com.worktime.pro.ui.motion.rememberHaptics
import com.worktime.pro.ui.components.AppWindowSize
import com.worktime.pro.ui.components.rememberAppWindowSize
import com.worktime.pro.ui.detail.DayDetailScreen
import com.worktime.pro.ui.home.HomeScreen
import com.worktime.pro.ui.manual.ManualEntryScreen
import com.worktime.pro.ui.mine.MineScreen
import com.worktime.pro.ui.rules.WorkRulesScreen
import com.worktime.pro.ui.settings.SettingsDataScreen
import com.worktime.pro.ui.settings.SettingsDisplayScreen
import com.worktime.pro.ui.settings.SettingsPunchScreen
import com.worktime.pro.ui.settings.SettingsReminderScreen
import com.worktime.pro.ui.settings.SettingsScreen
import com.worktime.pro.ui.summary.SummaryScreen
import com.worktime.pro.ui.theme.CustomThemeScreen
import com.worktime.pro.ui.theme.WorkTimeTheme
import com.worktime.pro.ui.today.TodayScreen
import com.worktime.pro.ui.update.UpdateDialogHost

/** 底部导航项定义 */
private data class TabItem(val route: String, val label: String, val icon: ImageVector)
private val TAB_ITEMS = listOf(
    TabItem("home", "首页", Icons.Filled.Home),
    TabItem("today", "今日", Icons.Filled.Today),
    TabItem("mine", "我的", Icons.Filled.Person)
)
private val TAB_ROUTES = TAB_ITEMS.map { it.route }.toSet()

/** 底部/侧栏 Tab 图标：选中时轻微放大回弹，给切换一个轻快的反馈 */
@Composable
private fun NavIcon(selected: Boolean, icon: ImageVector, label: String) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navIconScale"
    )
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WorkTimeApplication
        val factory = ViewModelFactory(app.repository, app.settings, app.backupManager)

        setContent {
            // 主题设置实时生效：用户切换后立即重组应用新配色
            val settings by app.settings.settings.collectAsState(
                initial = com.worktime.pro.data.datastore.AppSettings()
            )
            val updateState by app.updateManager.state.collectAsState()
            // 冷启动查一次更新：内部有 24 小时冷却与"跳过"记忆，重建 Activity 不会重复请求
            LaunchedEffect(Unit) { app.updateManager.checkOnLaunch() }
            WorkTimeTheme(
                themeMode = settings.themeMode,
                customThemeEnabled = settings.customThemeEnabled,
                customLight = settings.customLightColors,
                customDark = settings.customDarkColors,
                fontScale = settings.fontScale
            ) {
                CompositionLocalProvider(
                    LocalRepository provides app.repository,
                    LocalSettings provides app.settings
                ) {
                    val navController = rememberNavController()
                    val currentRoute by navController.currentBackStackEntryAsState()
                    val haptics = rememberHaptics()
                    // 记录最后停留的主 Tab，手动录入保存后回到来源页
                    var lastMainTab by remember { mutableStateOf("today") }
                    // 仅三个主 Tab 显示底部导航，进入子页面（详情/设置等）时隐藏
                    val showBottomBar = currentRoute?.destination?.route in TAB_ROUTES
                    // 宽屏与横屏把 Tab 收进侧边导航：手机横屏与平板纵向高度紧张，底部栏会挤压内容
                    val useRail = showBottomBar && rememberAppWindowSize() != AppWindowSize.COMPACT
                    // Tab 切换规则两处复用：清空中间栈并保存/恢复各 Tab 自己的返回栈
                    val onSelectTab: (TabItem) -> Unit = { tab ->
                        lastMainTab = tab.route
                        haptics.select()
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar && !useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    TAB_ITEMS.forEach { tab ->
                                        NavigationBarItem(
                                            selected = currentRoute?.destination?.route == tab.route,
                                            onClick = { onSelectTab(tab) },
                                            icon = { NavIcon(selected = currentRoute?.destination?.route == tab.route, icon = tab.icon, label = tab.label) },
                                            label = { Text(tab.label) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                            if (useRail) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    // Scaffold 已消费系统栏 insets，这里声明空 insets 避免二次留白
                                    windowInsets = WindowInsets(0, 0, 0, 0)
                                ) {
                                    Spacer(Modifier.height(12.dp))
                                    TAB_ITEMS.forEach { tab ->
                                        NavigationRailItem(
                                            selected = currentRoute?.destination?.route == tab.route,
                                            onClick = { onSelectTab(tab) },
                                            icon = { NavIcon(selected = currentRoute?.destination?.route == tab.route, icon = tab.icon, label = tab.label) },
                                            label = { Text(tab.label) },
                                            colors = NavigationRailItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                            // 单列内容限宽居中：平板与横屏不把一行文字拉成整屏宽
                            AdaptiveContent(modifier = Modifier.weight(1f)) {
                                AppNavHost(
                                    navController = navController,
                                    factory = factory,
                                    lastMainTab = lastMainTab,
                                    onCheckUpdate = { app.updateManager.checkManually() }
                                )
                            }
                        }
                    }

                    // 弹窗挂在导航图外层：任何页面（含设置页、二级页）都能收到更新提示
                    UpdateDialogHost(
                        state = updateState,
                        currentVersion = app.updateManager.currentVersionName,
                        onDownload = { app.updateManager.startDownload() },
                        onInstall = { app.updateManager.installPending() },
                        onOpenBrowserDownload = {
                            app.updateManager.dismiss()
                            runCatching {
                                startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(
                                            com.worktime.pro.domain.update.UpdateConfig.RELEASES_PAGE_URL
                                        )
                                    )
                                )
                            }
                        },
                        onDismiss = { app.updateManager.dismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    factory: ViewModelFactory,
    lastMainTab: String,
    onCheckUpdate: () -> Unit
) {
    // rememberUpdatedState 保证导航图里的旧 lambda 也能读到最新来源 Tab
    val originTab by rememberUpdatedState(lastMainTab)
    // 保存后回到进入手动录入前的主 Tab，避免从今日页跳回首页
    val popToOriginTab: () -> Unit = {
        if (!navController.popBackStack(originTab, inclusive = false)) {
            navController.popBackStack("today", inclusive = false)
        }
    }
    NavHost(
        navController = navController,
        startDestination = "today",
        enterTransition = {
            fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 4 }
        },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(140)) }
    ) {
        composable("home") {
            HomeScreen(
                factory = factory,
                onOpenDay = { date -> navController.navigate("day/$date") },
                onOpenManual = { navController.navigate("manual") }
            )
        }
        composable("today") {
            TodayScreen(
                factory = factory,
                onOpenManual = { navController.navigate("manual") },
                onOpenDayDetail = { date -> navController.navigate("day/$date") }
            )
        }
        composable("mine") {
            MineScreen(
                factory = factory,
                onOpenSettings = { navController.navigate("settings") },
                onOpenSummary = { navController.navigate("summary") },
                onOpenManual = { navController.navigate("manual") },
                onOpenHabits = { navController.navigate("habits") },
                onCheckUpdate = onCheckUpdate
            )
        }
        composable("habits") {
            com.worktime.pro.ui.habit.HabitScreen(
                factory = factory,
                onBack = { navController.popBackStack() }
            )
        }
        composable("rules") {
            WorkRulesScreen(
                factory = factory,
                onBack = { navController.popBackStack() }
            )
        }
        composable("custom-theme") {
            CustomThemeScreen(
                factory = factory,
                onBack = { navController.popBackStack() }
            )
        }
        composable("day/{date}") { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date").orEmpty()
            DayDetailScreen(
                dateKey = date,
                factory = factory,
                onBack = { navController.popBackStack() },
                onOpenHabits = { navController.navigate("habits") }
            )
        }
        composable("manual") {
            ManualEntryScreen(
                factory = factory,
                onSaved = popToOriginTab,
                onBack = { navController.popBackStack() }
            )
        }
        // 设置：一级目录 + 五个二级页面，返回键逐级回退
        composable("settings") {
            SettingsScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
                onOpenPunch = { navController.navigate("settings/punch") },
                onOpenDisplay = { navController.navigate("settings/display") },
                onOpenReminder = { navController.navigate("settings/reminder") },
                onOpenData = { navController.navigate("settings/data") }
            )
        }
        composable("settings/punch") {
            SettingsPunchScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
                onOpenRules = { navController.navigate("rules") },
                onOpenHabits = { navController.navigate("habits") }
            )
        }
        composable("settings/display") {
            SettingsDisplayScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
                onOpenCustomTheme = { navController.navigate("custom-theme") }
            )
        }
        composable("settings/reminder") {
            SettingsReminderScreen(
                factory = factory,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings/data") {
            SettingsDataScreen(
                factory = factory,
                onBack = { navController.popBackStack() }
            )
        }
        composable("summary") {
            SummaryScreen(
                factory = factory,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
