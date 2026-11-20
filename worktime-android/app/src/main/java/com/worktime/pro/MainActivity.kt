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
import androidx.compose.animation.core.tween
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.worktime.pro.ui.LocalRepository
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.camera.CameraScreen
import com.worktime.pro.ui.camera.ConfirmScreen
import com.worktime.pro.ui.detail.DayDetailScreen
import com.worktime.pro.ui.home.HomeScreen
import com.worktime.pro.ui.manual.ManualEntryScreen
import com.worktime.pro.ui.mine.MineScreen
import com.worktime.pro.ui.rules.WorkRulesScreen
import com.worktime.pro.ui.settings.SettingsScreen
import com.worktime.pro.ui.summary.SummaryScreen
import com.worktime.pro.ui.theme.CustomThemeScreen
import com.worktime.pro.ui.theme.WorkTimeTheme
import com.worktime.pro.ui.today.TodayScreen

/** 底部导航项定义 */
private data class TabItem(val route: String, val label: String, val icon: ImageVector)
private val TAB_ITEMS = listOf(
    TabItem("home", "首页", Icons.Filled.Home),
    TabItem("today", "今日", Icons.Filled.Today),
    TabItem("mine", "我的", Icons.Filled.Person)
)
private val TAB_ROUTES = TAB_ITEMS.map { it.route }.toSet()

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
                    // 记录最后停留的主 Tab，拍照/手动录入保存后回到来源页
                    var lastMainTab by remember { mutableStateOf("today") }
                    // 仅三个主 Tab 显示底部导航，进入子页面（拍照/详情/设置等）时隐藏
                    val showBottomBar = currentRoute?.destination?.route in TAB_ROUTES

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    TAB_ITEMS.forEach { tab ->
                                        val selected = currentRoute?.destination?.route == tab.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                lastMainTab = tab.route
                                                navController.navigate(tab.route) {
                                                    // 切换 Tab 时清空中间栈，回退直达起始页
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = { Icon(tab.icon, contentDescription = tab.label) },
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
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            AppNavHost(
                                navController = navController,
                                factory = factory,
                                lastMainTab = lastMainTab
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    factory: ViewModelFactory,
    lastMainTab: String
) {
    // rememberUpdatedState 保证导航图里的旧 lambda 也能读到最新来源 Tab
    val originTab by rememberUpdatedState(lastMainTab)
    // 保存后回到进入拍照/手动录入前的主 Tab，避免从今日页跳回首页
    val popToOriginTab: () -> Unit = {
        if (!navController.popBackStack(originTab, inclusive = false)) {
            navController.popBackStack("today", inclusive = false)
        }
    }
    NavHost(
        navController = navController,
        startDestination = "today",
        enterTransition = {
            fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 10 }
        },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(140)) }
    ) {
        composable("home") {
            HomeScreen(
                factory = factory,
                onOpenDay = { date -> navController.navigate("day/$date") },
                onOpenCamera = { navController.navigate("camera") },
                onOpenManual = { navController.navigate("manual") },
                onOpenHabits = { navController.navigate("habits") }
            )
        }
        composable("today") {
            TodayScreen(
                factory = factory,
                onOpenCamera = { navController.navigate("camera") },
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
                onOpenHabits = { navController.navigate("habits") }
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
        composable("camera") {
            CameraScreen(
                onCaptured = { bitmap ->
                    factory.sharedCameraViewModel.setPendingBitmap(bitmap)
                    navController.navigate("confirm")
                },
                onClose = { navController.popBackStack() }
            )
        }
        composable("confirm") {
            ConfirmScreen(
                factory = factory,
                onSaved = popToOriginTab,
                onRetake = { navController.popBackStack() }
            )
        }
        composable("day/{date}") { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date").orEmpty()
            DayDetailScreen(
                dateKey = date,
                factory = factory,
                onBack = { navController.popBackStack() }
            )
        }
        composable("manual") {
            ManualEntryScreen(
                factory = factory,
                onSaved = popToOriginTab,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                factory = factory,
                onBack = { navController.popBackStack() },
                onOpenHabits = { navController.navigate("habits") },
                onOpenRules = { navController.navigate("rules") },
                onOpenCustomTheme = { navController.navigate("custom-theme") }
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
