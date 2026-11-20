/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/evidence/GalleryPublisher.kt
 * @Description: 把确认后的凭证写入系统相册 Pictures/工时助手，失败不阻塞打卡入库
 */
package com.worktime.pro.domain.evidence

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object GalleryPublisher {

    private const val ALBUM = "工时助手"

    fun publish(context: Context, source: File, displayName: String): Result<Uri> = runCatching {
        require(source.isFile && source.length() > 0) { "凭证文件不存在" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishScoped(context, source, displayName)
        } else {
            publishLegacy(context, source, displayName)
        }
    }

    /** Android 10+：MediaStore 分区存储，无需存储权限 */
    private fun publishScoped(context: Context, source: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册条目")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { it.copyTo(output) }
            } ?: error("无法写入相册")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Android 8–9：写入公共 Pictures 目录后通知媒体扫描，需要存储写权限 */
    @Suppress("DEPRECATION")
    private fun publishLegacy(context: Context, source: File, displayName: String): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM
        )
        if (!dir.exists() && !dir.mkdirs()) error("无法创建相册目录")
        val target = File(dir, displayName)
        source.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf("image/jpeg"),
            null
        )
        return Uri.fromFile(target)
    }
}
