/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/UpdateChecker.kt
 * @Description: 按优先级依次尝试多个更新源（Release API → jsDelivr 清单 → 代理 raw 清单），仅此一处发起网络请求
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

    /** 单源抓取结果：Body 表示拿到了响应文本，Failure 携带面向用户的可读原因 */
    private sealed interface FetchResult {
        data class Body(val text: String) : FetchResult
        data class Failure(val userMessage: String) : FetchResult
    }

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) { checkAllSources() }

    /**
     * 业务流转要求：国内手机直连 GitHub 大概率超时，单一数据源会让大量用户永远查不到更新。
     * 因此按「信息最全 → 国内最可达」的顺序逐源尝试：Release API（含更新说明与安装包大小）失败后
     * 走 jsDelivr CDN 清单，再失败走代理 raw 清单；任一源给出结论（含「已是最新」）即立刻返回，
     * 全部失败时展示最后一个可读原因，不吞异常。
     */
    private fun checkAllSources(): UpdateCheckResult {
        var lastFailure = DEFAULT_FAILURE_MESSAGE

        when (val response = fetch(apiUrl)) {
            is FetchResult.Body -> when (val result = UpdateResponseParser.parse(response.text, currentVersionName)) {
                is UpdateCheckResult.Failed -> lastFailure = result.message
                else -> return result
            }
            is FetchResult.Failure -> lastFailure = response.userMessage
        }

        val manifestUrls = listOf(
            UpdateConfig.UPDATE_JSON_URL_JSDELIVR,
            UpdateConfig.UPDATE_JSON_URL_RAW_PROXY
        )
        for (url in manifestUrls) {
            when (val response = fetch(url)) {
                is FetchResult.Body -> when (val result = UpdateResponseParser.parseManifest(response.text, currentVersionName)) {
                    is UpdateCheckResult.Failed -> lastFailure = result.message
                    else -> return result
                }
                // 镜像源失败原因多是网络不可达，用通用文案即可，保留前一源更具行动价值的失败原因
                is FetchResult.Failure -> if (lastFailure == DEFAULT_FAILURE_MESSAGE) lastFailure = response.userMessage
            }
        }
        return UpdateCheckResult.Failed(lastFailure)
    }

    /**
     * 通用文本抓取：HTTP 状态码映射成面向用户的原因，网络层异常统一归为网络不可达。
     * 返回 Failure 表示「该源本次不可用」而不是整体失败，由上层换源重试。
     */
    private fun fetch(url: String): FetchResult {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub 要求带 UA，同时便于在服务端区分版本来源
                setRequestProperty("User-Agent", "WorkTimePro/$currentVersionName")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        } catch (e: Exception) {
            return FetchResult.Failure(DEFAULT_FAILURE_MESSAGE)
        }

        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    FetchResult.Body(connection.inputStream.bufferedReader().use { it.readText() })
                // 仓库没有任何已发布 Release 时 GitHub 返回 404，属于「没发过版」而不是网络错误
                HttpURLConnection.HTTP_NOT_FOUND ->
                    FetchResult.Failure("更新服务暂无发布信息")
                // 未认证接口按 IP 限流 60 次/小时，共享出口 IP 下很容易触发
                TOO_MANY_REQUESTS, HTTP_FORBIDDEN ->
                    FetchResult.Failure("更新服务请求过于频繁，请稍后再试")
                else ->
                    FetchResult.Failure("更新服务暂时不可用（HTTP ${connection.responseCode}）")
            }
        } catch (e: Exception) {
            FetchResult.Failure(DEFAULT_FAILURE_MESSAGE)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val TOO_MANY_REQUESTS = 429
        const val HTTP_FORBIDDEN = 403
        const val DEFAULT_FAILURE_MESSAGE = "无法连接更新服务器，请检查网络"
    }
}
