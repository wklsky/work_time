/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/VersionComparator.kt
 * @Description: 版本号比较，用于判断 GitHub Release 的 tag 是否比本机版本更新
 */
package com.worktime.pro.domain.update

object VersionComparator {

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    /**
     * 逐段按数字比较，缺失段补 0，所以 2.1 与 2.1.0 视为同一版本。
     * 只取数字段：tag 的 `v` 前缀、`-beta` / `+build` 后缀都不参与比较——
     * 预发布版若被标成 latest 会被当成正式更新，这里不做区分是为了避免把 2.1.0-beta 判定为比 2.1.0 新。
     */
    fun compare(left: String, right: String): Int {
        val a = segments(left)
        val b = segments(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun segments(raw: String): List<Int> = raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
}
