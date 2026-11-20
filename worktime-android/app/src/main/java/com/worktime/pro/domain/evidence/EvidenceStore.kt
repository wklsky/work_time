/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/evidence/EvidenceStore.kt
 * @Description: 凭证文件管理，统一相对路径存储并兼容旧版绝对路径
 */
package com.worktime.pro.domain.evidence

import android.content.Context
import android.graphics.Bitmap
import com.worktime.pro.util.Constants
import java.io.File

object EvidenceStore {

    fun directory(context: Context): File =
        File(context.filesDir, Constants.EVIDENCE_DIR).apply { mkdirs() }

    /** 保存凭证，返回相对文件名（只存文件名，换机/清缓存后仍可判断缺失） */
    fun save(context: Context, bitmap: Bitmap, quality: Int = 70): String {
        val file = File(directory(context), "evidence_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return file.name
    }

    /** 解析凭证文件；兼容旧数据里的绝对路径，文件缺失返回 null */
    fun resolve(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) return null
        val direct = File(path)
        val candidate = if (direct.isAbsolute) direct else File(directory(context), path)
        return candidate.takeIf { it.isFile && it.length() > 0 }
    }
}
