/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/MainActivity.kt
 * @Description: 应用主 Activity，承载 Compose 导航图与全局依赖注入
 */
package com.worktime.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.worktime.pro.ui.LocalRepository
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.camera.CameraScreen
import com.worktime.pro.ui.camera.ConfirmScreen
import com.worktime.pro.ui.detail.DayDetailScreen
import com.worktime.pro.ui.home.HomeScreen
import com.worktime.pro.ui.manual.ManualEntryScreen
import com.worktime.pro.ui.settings.SettingsScreen
import com.worktime.pro.ui.summary.SummaryScreen
import com.worktime.pro.ui.theme.WorkTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as WorkTimeApplication
        val factory = ViewModelFactory(app.repository, app.settings, app.backupManager)

        setContent {
            WorkTimeTheme {
                CompositionLocalProvider(
                    LocalRepository provides app.repository,
                    LocalSettings provides app.settings
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                factory = factory,
                                onOpenDay = { date -> navController.navigate("day/$date") },
                                onOpenCamera = { navController.navigate("camera") },
                                onOpenManual = { navController.navigate("manual") },
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenSummary = { navController.navigate("summary") }
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
                                onSaved = {
                                    navController.popBackStack("home", inclusive = false)
                                },
                                onRetake = { navController.popBackStack() }
                            )
                        }
                        composable("day/{date}") { backStackEntry ->
                            val date = backStackEntry.arguments?.getString("date").orEmpty()
                            DayDetailScreen(
                                dateKey = date,
                                factory = factory,
                                onBack = { navController.popBackStack() },
                                onViewEvidence = { /* 凭证查看由系统图片查看器处理，见设置页导出 */ }
                            )
                        }
                        composable("manual") {
                            ManualEntryScreen(
                                factory = factory,
                                onSaved = { navController.popBackStack("home", inclusive = false) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
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
            }
        }
    }
}
