/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/UpdateChecker.kt
 * @Description: 读取 GitHub Release 最新版本并与本机版本比对，仅此一处发起网络请求
 */
package com.worktime.pro.domain.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(
    private val currentVersionName: String,
    private val apiUrl: String = UpdateConfig.RELEASE_API_URL
) {

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) { requestLatestRelease() }

    private fun requestLatestRelease(): UpdateCheckResult {
        val connection = try {
            (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub 要求带 UA，同时便于在服务端区分版本来源
                setRequestProperty("User-Agent", "WorkTimePro/$currentVersionName")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        } catch (e: Exception) {
            return UpdateCheckResult.Failed("无法连接更新服务器，请检查网络")
        }

        return try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> UpdateResponseParser.parse(
                    body = connection.inputStream.bufferedReader().use { it.readText() },
                    currentVersionName = currentVersionName
                )
                // 仓库没有任何已发布 Release 时 GitHub 返回 404，属于「没发过版」而不是错误
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheckResult.UpToDate
                // 未认证接口按 IP 限流 60 次/小时，共享出口 IP 下很容易触发
                TOO_MANY_REQUESTS, HTTP_FORBIDDEN -> UpdateCheckResult.Failed("更新服务请求过于频繁，请稍后再试")
                else -> UpdateCheckResult.Failed("更新服务暂时不可用（HTTP $code）")
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed("更新检查失败，请检查网络")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val TOO_MANY_REQUESTS = 429
        const val HTTP_FORBIDDEN = 403
    }
}
