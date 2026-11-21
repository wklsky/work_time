/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/UpdateInstaller.kt
 * @Description: 更新包下载与安装，下载交给系统 DownloadManager，安装走 FileProvider + 系统安装器
 */
package com.worktime.pro.domain.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** 安装拉起结果：缺权限时必须引导用户去系统设置，不能静默失败 */
sealed interface InstallResult {
    data object Started : InstallResult
    data object NeedPermission : InstallResult
    data class Failed(val message: String) : InstallResult
}

object UpdateInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"
    private val APK_DIR = Environment.DIRECTORY_DOWNLOADS

    data class Progress(val percent: Int, val finished: Boolean, val failed: Boolean)

    private fun apkFileName(info: UpdateInfo): String = "worktime-${info.versionName}.apk"

    /**
     * 用系统 DownloadManager 而不是自己写流：62MB 的包需要断点续传与后台下载能力，
     * 进程被杀也不中断，通知栏进度也省掉自绘。
     */
    fun enqueue(context: Context, info: UpdateInfo): Long? {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return null
        // 同版本重复点击时先删旧文件，避免 DownloadManager 因目标已存在而失败
        apkFile(context, info)?.takeIf { it.exists() }?.delete()

        val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("工时助手 ${info.versionName}")
            setDescription("正在下载更新包")
            setMimeType(APK_MIME)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, APK_DIR, apkFileName(info))
            // 应用内私有目录下载不需要存储权限；用户主动点更新，用移动网络也允许
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }
        return runCatching { manager.enqueue(request) }.getOrNull()
    }

    /** 轮询下载进度：DownloadManager 没有回调可按任务订阅，只能查询 */
    fun query(context: Context, downloadId: Long): Progress {
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: return Progress(0, finished = false, failed = true)
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) return Progress(0, finished = false, failed = true)
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val percent = if (total > 0) ((downloaded.coerceAtLeast(0) * 100) / total).toInt() else 0
                Progress(
                    percent = percent.coerceIn(0, 100),
                    finished = status == DownloadManager.STATUS_SUCCESSFUL,
                    failed = status == DownloadManager.STATUS_FAILED
                )
            }
        }.getOrElse { Progress(0, finished = false, failed = true) }
    }

    fun apkFile(context: Context, info: UpdateInfo): File? =
        context.getExternalFilesDir(APK_DIR)?.resolve(apkFileName(info))

    /** Android 8.0 起安装 APK 需要「安装未知应用」授权，未授权会直接被系统拦掉 */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun install(context: Context, apk: File): InstallResult {
        if (!apk.exists()) return InstallResult.Failed("安装包不存在，请重新下载")
        if (!canInstallPackages(context)) {
            openInstallPermissionSettings(context)
            return InstallResult.NeedPermission
        }

        // 私有目录里的文件不能直接给安装器路径，必须用 FileProvider 授权读
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        }.getOrElse { return InstallResult.Failed("安装包读取失败") }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }
            .fold({ InstallResult.Started }, { InstallResult.Failed("无法拉起系统安装器") })
    }
}
